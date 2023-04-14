package com.dotscene.dronecontroller;

import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.*;

public class TCPClientTest {

  @Test
  public void encodingTest() {
    // Test boolean encoding
    byte buffer[] = new byte[1];
    TCPClient.encodeBool(buffer, 0, true);
    assertTrue(buffer[0] != 0);

    // Test int16 encoding
    buffer = new byte[TCPClient.encodedUInt16Size()];
    TCPClient.encodeUInt16(buffer, 0, (short) 14123);
    byte result[] = {55, 43};
    assertArrayEquals(result, buffer);

    // Test int32 encoding
    buffer = new byte[TCPClient.encodedUInt32Size()];
    TCPClient.encodeUInt32(buffer, 0, 120754135);
    result = new byte[]{7, 50, -113, -41};
    assertArrayEquals(result, buffer);


    // Test strings
    buffer = new byte[TCPClient.encodedStringSize("Hello World!")];
    try {
      TCPClient.encodeString(buffer, 0, "Hello World!");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      fail();
    }
    result = new byte[]{0, 12, 72, 101, 108, 108, 111, 32, 87, 111, 114, 108, 100, 33};
    assertArrayEquals(result, buffer);

    // Test string arrays
    String a[] = {"Lorem ipsum", "dolor sit", "amet"};
    buffer = new byte[TCPClient.encodedStringArraySize(a)];
    try {
      TCPClient.encodeStringArray(buffer, 0, a);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      fail();
    }
    result = new byte[]{0, 3, 0, 11, 76, 111, 114, 101, 109, 32, 105, 112, 115, 117, 109, 0, 9, 100,
        111, 108, 111, 114, 32, 115, 105, 116, 0, 4, 97, 109, 101, 116};
    assertArrayEquals(result, buffer);
  }

  @Test
  public void decodingTest() {
    try {
      String s = "Hello World!";
      String a[] = {"Lorem ipsum", "dolor sit", "amet"};
      TCPClient.PacketEncoder encoder = new TCPClient.PacketEncoder();
      encoder.addBoolean(true);
      encoder.addUInt16((short) 30000);
      encoder.addUInt32(123408972);
      encoder.addString(s);
      encoder.addStringArray(a);

      TCPClient.PacketDecoder decoder = new TCPClient.PacketDecoder(encoder.getBuffer());
      assertTrue(decoder.getBoolean());
      assertEquals((short)30000, decoder.getUInt16());
      assertEquals(123408972, decoder.getUInt32());
      assertEquals(s, decoder.getString());
      assertArrayEquals(a, decoder.getStringArray());
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
      fail();
    }
  }
}
