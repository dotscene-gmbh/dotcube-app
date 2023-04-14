package com.dotscene.dronecontroller;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.text.format.Formatter;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Florian Kramer on 3/3/17.
 */

public class TCPClient extends Thread {

  static final int PACKET_HEADER_SIZE = 6;
  static final int PACKET_SIZE_BYTES = 4;
  static final int PACKET_TYPE_BYTES = 2;

  public enum ConnectionError {
    HOST_UNKNOWN,
    HOST_UNREACHABLE,
    SERVER_NOT_RUNNING,
    GENERIC
  }

  private class Message {
    int packetType;
    byte[] data;
    OutputStream stream;
  }

  private class MessageDispatcher extends Thread {

    public boolean shouldRun = true;

    private Semaphore numMessagesSemaphore = new Semaphore(0, true);
    private ReentrantLock messageQueueLock = new ReentrantLock();
    private ArrayDeque<Message> messageQueue = new ArrayDeque<>();

    @Override
    public void run() {
      while (shouldRun) {
        try {
          numMessagesSemaphore.acquire();
          if (!shouldRun) {
            return;
          }
          Message message = null;
          messageQueueLock.lock();
          try {
            message = messageQueue.pop();
          } catch (Exception e) {
            e.printStackTrace();
          }
          messageQueueLock.unlock();
          if (message != null && message.stream != null) {
            // Ensure the data array is not null
            if (message.data == null) {
              message.data = new byte[0];
            }
            byte data[] = new byte[message.data.length + PACKET_HEADER_SIZE];

            // Write four bytes of message length into the buffer
            int msgLength = message.data.length;
            encodeUInt32(data, 0, msgLength);

            // write the packet type
            encodeUInt16(data, PACKET_SIZE_BYTES, (short) message.packetType);
            System.arraycopy(message.data, 0, data, PACKET_HEADER_SIZE, message.data.length);
            message.stream.write(data);
            message.stream.flush();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    public void queueMessage(OutputStream stream, final int packetType, final byte[] data) {
      Message msg = new Message();
      msg.packetType = packetType;
      msg.data = data;
      msg.stream = stream;
      messageQueueLock.lock();
      try {
        messageQueue.add(msg);
      } catch (Exception e) {
        e.printStackTrace();
      }
      messageQueueLock.unlock();
      numMessagesSemaphore.release();
    }

    public void close() {
      shouldRun = false;
      numMessagesSemaphore.release();
    }
  }


  private Socket socket;
  private InputStream fromServer;
  private OutputStream toServer;
  private boolean shouldRun = true;

  TCPListener listener = null;

  private static final int NUM_CONNECTION_RETRIES = 6;
  private static final int CONNECTION_RETRY_DELAY = 5000;

  private byte[] messageBuffer;
  private int bytesToRead = 0;
  private int lastLengthByteRead = PACKET_SIZE_BYTES;

  MessageDispatcher dispatcher;

  private String ipAddr = "127.0.0.1";
  private int port = 25542;

  public TCPClient(String ipAddr, int port) {
    this.ipAddr = ipAddr;
    this.port = port;
    dispatcher = new MessageDispatcher();
  }

  public void connect() {
    // open socket on separate thread and start listening on it
    start();
    dispatcher.start();
  }

  public void disconnect() {
    Log.e(getClass().getSimpleName(), "Stack on tcp client disconnect:");
    Thread.dumpStack();
    shouldRun = false;
    // close connection to the server. This will interrupt any reads on the fromServer stream
    if (socket != null) {  // socket can be null if it wasn't opened properly
      try {
        socket.close();
      } catch (IOException e) {
        Log.e(getClass().getSimpleName(), "error closing tcp connection:", e);
      }
    }
    socket = null;
    dispatcher.close();
    dispatcher = new MessageDispatcher();
  }

  private void debugPrintBuffer(byte[] b, int length) {
    String str = "";
    for (int i = 0; i < length; i++) {
      str += b[i] + ", ";
    }
    Log.d(getClass().getSimpleName(), str);
  }

  public void run() {
    boolean connected = false;
    ConnectionError connectionError = ConnectionError.GENERIC;
    // retry connecting multiple times
    for (int numTries = 0; numTries < NUM_CONNECTION_RETRIES && !connected; numTries++) {
      // open connection to the server
      try {
        Log.d(getClass().getSimpleName(), "Trying to connect to " + ipAddr + ":" + port);
        socket = new Socket(ipAddr, port);
        fromServer = socket.getInputStream();
        toServer = socket.getOutputStream();
        // ensures the listening thread will keep on running
        shouldRun = true;
        if (listener != null) {
          listener.onTCPConnected();
        }
        connected = true;
      } catch (UnknownHostException e) {
        connectionError = ConnectionError.HOST_UNKNOWN;
        Log.e(getClass().getSimpleName(), "error connecting via tcp:", e);
        try {
          Thread.sleep(CONNECTION_RETRY_DELAY);
        } catch (InterruptedException e1) {
          Log.e(getClass().getSimpleName(), "error when sleeping:", e);
        }
      } catch (Exception e) {
        Log.e(getClass().getSimpleName(), "error connecting via tcp:", e);
        try {
          Thread.sleep(CONNECTION_RETRY_DELAY);
        } catch (InterruptedException e1) {
          Log.e(getClass().getSimpleName(), "error when sleeping:", e);
        }
      }
    }
    if (!connected) {
      if (listener != null) {
        if (connectionError == ConnectionError.GENERIC) {
          Runtime runtime = Runtime.getRuntime();
          try {
            Process ping = runtime.exec("/system/bin/ping -c 1 " + ipAddr);
            int status = ping.waitFor();
            if (status == 0) {
              connectionError = ConnectionError.SERVER_NOT_RUNNING;
            } else {
              connectionError = ConnectionError.HOST_UNREACHABLE;
            }
          } catch (Exception e) {
            Log.e(getClass().getSimpleName(), "Unble to call ping: ", e);
          }
        }
        listener.onTCPConnectionError(connectionError);
      } else {
        Log.w(getClass().getSimpleName(), "No listener registered for tcp events.");
      }
      return;
    }

    byte[] buffer = new byte[2048];
    int numRead = 0;
    // while we should still read and the other side didn't close the connection
    while (shouldRun && numRead > -1) {
      try {
        numRead = fromServer.read(buffer, 0, 2048);
        // don't try to do anything if the stream has received EOF
        if (numRead < 0) {
          break;
        }
        // offset into the buffer
        int pos = 0;
        // process messages
        while (pos < numRead) {
          // read next message length
          while (lastLengthByteRead > 0 && pos < numRead) {
            bytesToRead += (buffer[pos] & 0xFF) << ((lastLengthByteRead - 1) * 8);
            lastLengthByteRead--;
            if (lastLengthByteRead == 0) {
              // There are two more bytes for the type header that are not counted in the length
              // header.
              bytesToRead += PACKET_TYPE_BYTES;
            }
            pos++;
          }
          // Has the full packet size header arrived and is there still more data available,
          // or do we not need any more.
          if (lastLengthByteRead == 0 && (pos < numRead || bytesToRead == 0)) {
            int availableBytes = Math.min(numRead - pos, bytesToRead);
            if (messageBuffer == null) {
              messageBuffer = Arrays.copyOfRange(buffer, pos, pos + availableBytes);
            } else {
              // concatenate the current message buffer with the new data
              int l = messageBuffer.length;
              messageBuffer = Arrays.copyOf(messageBuffer, l + availableBytes);
              System.arraycopy(buffer, pos, messageBuffer, l, availableBytes);
            }
            bytesToRead -= availableBytes;
            if (bytesToRead < 0) {
              // error while reading
              Log.e(getClass().getSimpleName(), "TCP reading error, consumed to many bytes");
            }
            if (bytesToRead == 0) {
              if (listener != null) {
                try {
                  listener.onTcpMessageReceived(messageBuffer);
                } catch (Exception e) {
                  Log.e(getClass().getSimpleName(), "Error during the tcp message handling:",
                      e);
                }
              }
              messageBuffer = null;
              bytesToRead = 0;
              lastLengthByteRead = PACKET_SIZE_BYTES;
            }
            pos += availableBytes;
          }
        }
      } catch (SocketException e1) {
        if (e1.getMessage().equals("Socket closed")) {
          Log.w(getClass().getSimpleName(), "socket closed. Should the socket run: " + shouldRun, e1);
        } else {
          Log.e(getClass().getSimpleName(), "error while reading from tcp:", e1);
        }
        // The connection was reset or is otherwise damaged, we should disconnect
        disconnect();
      } catch (IOException e) {
        Log.e(getClass().getSimpleName(), "error while reading from tcp:", e);
      }
    }
    if (listener != null) {
      listener.onTCPDisconnected();
    }
    dispatcher.close();
  }

  public void sendMessage(final int packetType, final byte[] data) {
    dispatcher.queueMessage(toServer, packetType, data);
  }

  public static boolean decodeBool(byte buffer[], int offset) {
    return buffer[offset] != 0;
  }

  public static int encodedBoolSize() {
    return 1;
  }

  public static int decodeUInt32(byte buffer[], int offset) {
    return ((buffer[offset] & 0xFF) << 24) + ((buffer[offset + 1] & 0xFF) << 16) + ((buffer[offset + 2] & 0xFF) << 8) + ((buffer[offset + 3] & 0xFF));
  }

  public static int encodedUInt32Size() {
    return 4;
  }

  public static long decodeUInt64(byte buffer[], int offset) {
    return ((long)(buffer[offset] & 0xFF) << 56L) + ((long)(buffer[offset + 1] & 0xFF) << 48L) +
        ((long)(buffer[offset + 2] & 0xFF) << 40L) + ((long)(buffer[offset + 3] & 0xFF) << 32L) +
        ((long)(buffer[offset + 4] & 0xFF) << 24L) + ((long)(buffer[offset + 5] & 0xFF) << 16L) +
        ((long)(buffer[offset + 6] & 0xFF) << 8L) + ((long)(buffer[offset + 7] & 0xFF));
  }

  public static int encodedUInt64Size() {
    return 8;
  }

  public static short decodeUInt16(byte buffer[], int offset) {

    return (short) (((buffer[offset] & 0xFF) << 8) + ((buffer[offset + 1] & 0xFF) << 0));
  }

  public static int encodedUInt16Size() {
    return 2;
  }

  public static String decodeString(byte buffer[], int offset) throws UnsupportedEncodingException {
    short size = decodeUInt16(buffer, offset);
    offset += encodedUInt16Size();
    byte[] strBuffer = new byte[size];
    System.arraycopy(buffer, offset, strBuffer, 0, size);
    return new String(strBuffer, StandardCharsets.UTF_8);
  }

  public static int encodedStringSize(byte buffer[], int offset) {
    short size = decodeUInt16(buffer, offset);
    return size + encodedUInt16Size();
  }

  public static int encodedStringSize(String s) {
    int size = 0;
    size = s.getBytes(StandardCharsets.UTF_8).length;
    return size + encodedUInt16Size();
  }

  public static String[] decodeStringArray(byte buffer[], int offset) throws UnsupportedEncodingException {
    short size = decodeUInt16(buffer, offset);
    offset += encodedUInt16Size();
    String[] val = new String[size];
    for (int i = 0; i < size; i++) {
      val[i] = decodeString(buffer, offset);
      offset += encodedStringSize(buffer, offset);
    }
    return val;
  }

  public static int encodedStringArraySize(byte buffer[], int offset) {
    short count = decodeUInt16(buffer, offset);
    offset += encodedUInt16Size();
    int size = encodedUInt16Size();
    for (int i = 0; i < count; i++) {
      int s = encodedStringSize(buffer, offset);
      offset += s;
      size += s;
    }
    return size;
  }

  public static int encodedStringArraySize(String a[]) {
    int size = encodedUInt16Size();
    for (String s : a) {
      size += encodedStringSize(s);
    }
    return size;
  }

  public static void encodeBool(byte[] buffer, int offset, boolean val) {
    if (val) {
      buffer[offset] = 1;
    } else {
      buffer[offset] = 0;
    }
  }

  public static void encodeUInt16(byte[] buffer, int offset, short val) {
    // Java bytes use twos complement
    if ((val & 0xFF) > 127) {
      buffer[offset + 1] = (byte) ((val & 0xFF) - 256);
    } else {
      buffer[offset + 1] = (byte) (val & 0xFF);
    }
    if (((val >> 8) & 0xFF) > 127) {
      buffer[offset] = (byte) (((val >> 8) & 0xFF) - 256);
    } else {
      buffer[offset] = (byte) ((val >> 8) & 0xFF);
    }
  }

  public static void encodeUInt32(byte[] buffer, int offset, int val) {
    // Java bytes use twos complement
    if ((val & 0xFF) > 127) {
      buffer[offset + 3] = (byte) ((val & 0xFF) - 256);
    } else {
      buffer[offset + 3] = (byte) (val & 0xFF);
    }
    if (((val >> 8) & 0xFF) > 127) {
      buffer[offset + 2] = (byte) (((val >> 8) & 0xFF) - 256);
    } else {
      buffer[offset + 2] = (byte) ((val >> 8) & 0xFF);
    }
    if (((val >> 16) & 0xFF) > 127) {
      buffer[offset + 1] = (byte) (((val >> 16) & 0xFF) - 256);
    } else {
      buffer[offset + 1] = (byte) ((val >> 16) & 0xFF);
    }
    if (((val >> 24) & 0xFF) > 127) {
      buffer[offset] = (byte) (((val >> 24) & 0xFF) - 256);
    } else {
      buffer[offset] = (byte) ((val >> 24) & 0xFF);
    }
  }

  public static void encodeUInt64(byte[] buffer, int offset, long val) {
    // Java bytes use twos complement
    for (int i = 0; i < 8; i++) {
      if (((val >> (8L * i)) & 0xFF) > 127) {
        buffer[offset + 7 - i] = (byte) (((val >> (8L * i)) & 0xFF) - 256);
      } else {
        buffer[offset + 7 - i] = (byte) ((val >> (8L * i)) & 0xFF);
      }
    }
  }


  public static void encodeString(byte[] buffer, int offset, String val) throws UnsupportedEncodingException {
    byte str[] = val.getBytes("UTF-8");
    encodeUInt16(buffer, offset, (short) str.length);
    offset += encodedUInt16Size();
    System.arraycopy(str, 0, buffer, offset, str.length);
  }

  public static void encodeStringArray(byte[] buffer, int offset, String[] val) throws UnsupportedEncodingException {
    encodeUInt16(buffer, offset, (short) val.length);
    offset += encodedUInt16Size();
    for (int i = 0; i < val.length; i++) {
      encodeString(buffer, offset, val[i]);
      offset += encodedStringSize(buffer, offset);
    }
  }

  public void setTCPListener(TCPListener l) {
    listener = l;
  }

  public static class PacketEncoder {
    private byte[] buffer = new byte[64];
    private int offset = 0;

    void addBoolean(boolean b) {
      int space = encodedBoolSize();
      ensureSpace(space);
      encodeBool(buffer, offset, b);
      offset += space;
    }

    void addUInt16(short s) {
      int space = encodedUInt16Size();
      ensureSpace(space);
      encodeUInt16(buffer, offset, s);
      offset += space;
    }

    void addUInt32(int i) {
      int space = encodedUInt32Size();
      ensureSpace(space);
      encodeUInt32(buffer, offset, i);
      offset += space;
    }

    void addUInt64(long i) {
      int space = encodedUInt64Size();
      ensureSpace(space);
      encodeUInt64(buffer, offset, i);
      offset += space;
    }

    void addString(String s) throws UnsupportedEncodingException {
      int space = encodedStringSize(s);
      ensureSpace(space);
      encodeString(buffer, offset, s);
      offset += space;
    }


    void addStringArray(String[] s) throws UnsupportedEncodingException {
      int space = encodedStringArraySize(s);
      ensureSpace(space);
      encodeStringArray(buffer, offset, s);
      offset += space;
    }

    byte[] getBuffer() {
      if (buffer.length > offset) {
        byte[] tmp = Arrays.copyOf(buffer, offset);
        buffer = tmp;
      }
      return buffer;
    }

    private void ensureSpace(int space) {
      if (buffer.length - offset < space) {
        int newlen = buffer.length * 2;
        while (newlen - offset < space) {
          newlen *= 2;
        }
        byte[] tmp = Arrays.copyOf(buffer, newlen);
        buffer = tmp;
      }
    }
  }


  public static class PacketDecoder {
    private byte buffer[];
    int offset = 0;

    public PacketDecoder(byte[] buffer) {
      this.buffer = buffer;
    }

    boolean getBoolean() {
      boolean b = decodeBool(buffer, offset);
      offset += encodedBoolSize();
      return b;
    }


    short getUInt16() {
      short s = decodeUInt16(buffer, offset);
      offset += encodedUInt16Size();
      return s;
    }


    int getUInt32() {
      int i = decodeUInt32(buffer, offset);
      offset += encodedUInt32Size();
      return i;
    }

    long getUInt64() {
      long i = decodeUInt64(buffer, offset);
      offset += encodedUInt64Size();
      return i;
    }

    String getString() throws UnsupportedEncodingException {
      String s = decodeString(buffer, offset);
      offset += encodedStringSize(buffer, offset);
      return s;
    }


    String[] getStringArray() throws UnsupportedEncodingException {
      String a[] = decodeStringArray(buffer, offset);
      offset += encodedStringArraySize(buffer, offset);
      return a;
    }
  }

  interface TCPListener {
    void onTCPConnected();

    void onTCPConnectionError(ConnectionError cause);

    void onTCPDisconnected();

    void onTcpMessageReceived(byte[] message);
  }
}
