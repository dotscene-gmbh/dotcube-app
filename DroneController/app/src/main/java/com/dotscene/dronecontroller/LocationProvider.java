package com.dotscene.dronecontroller;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Florian Kramer on 8/25/17.
 */

public class LocationProvider implements LocationListener {

  // STATIC MEMBERS
  private static Lock instanceLock = new ReentrantLock();
  private static LocationProvider instance;
  // buffer the last accepted location
  private static Location lastAcceptedLocation = null;

  public static void acquireLocation(OnLocationAcquiredListener consumer, Context context) {
    Log.d(LocationProvider.class.getSimpleName(), "Acquiring a location.");
    LocationManager provider = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    boolean createdNewInstance = false;
    // check if there already is an instance of the LocationProvider. In that case we only register
    // a new OnLocationAcquiredListener. Otherwise we create a new instance of the LocationProvider
    // and start the location acquisition process.
    instanceLock.lock();
    if (instance == null) {
      Log.d(LocationProvider.class.getSimpleName(), "instance is null, creating a new one");
      instance = new LocationProvider(provider);
      createdNewInstance = true;
    }
    instance.addOnLocationAcquiredListener(consumer);
    // the lock needs to be kept until now to ensure the LocationProvider doesn't find a location
    // in the meantime and deletes itself, preventing us from adding a new
    // OnLocationAcquiredListener to it
    instanceLock.unlock();

    // if we had to create a new instance we need to initialize the acquisition of the location
    if (createdNewInstance) {
      instance.acquireLocation();
    }
  }

  // INSTANCE DEFINITION
  private final ArrayList<OnLocationAcquiredListener> onLocationAcquiredListeners = new ArrayList<>();

  private LocationManager locationManager;
  private Location currentBestLocation;
  private long startTime;
  private int numLocationsAcquired = 0;

  private boolean hasAcquiredLocation = false;

  public LocationProvider(LocationManager locationManager) {
    this.locationManager = locationManager;
    startTime = System.currentTimeMillis();
  }

  public void acquireLocation() {
    try {
      Log.d(LocationProvider.class.getSimpleName(), "checking for buffered locations");
      updateLocation(locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER));
      updateLocation(locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER));
      updateLocation(lastAcceptedLocation);

      // we may have just accepted a buffered location
      if (!hasAcquiredLocation) {
        Log.d(LocationProvider.class.getSimpleName(), "no suitable buffered location found, listening for new locations");
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
      }
    } catch (SecurityException e) {
      Log.w("LOG-GPS", "Unable to acquire location: missing permissions \n" + e.getMessage());
      locationManager.removeUpdates(this);
      instanceLock.lock();
      instance = null;
      instanceLock.unlock();
    }
  }

  public void addOnLocationAcquiredListener(OnLocationAcquiredListener l) {
    synchronized (onLocationAcquiredListeners) {
      onLocationAcquiredListeners.add(l);
    }
  }

  public void removeOnLocationAcquiredListener(OnLocationAcquiredListener l) {
    synchronized (onLocationAcquiredListeners) {
      onLocationAcquiredListeners.remove(l);
    }
  }

  @Override
  public void onLocationChanged(Location location) {
    Log.d(LocationProvider.class.getSimpleName(), "received location update from: " + location.getProvider() + ", " + location.getLatitude() + ", " + location.getLongitude() + ", " + location.getAccuracy());
    numLocationsAcquired++;
    // if more than 10 location updates were received, and we have been waiting for more than 3 minutes
    if (numLocationsAcquired > 10 && System.currentTimeMillis() - startTime > 180000) {
      Log.d(LocationProvider.class.getSimpleName(), "We do not seem to be able to acquire a good location, using the last one we found");
      // use the currently best location, no matter its quality
      onAcquiredLocation();
    } else {
      updateLocation(location);
    }
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {

  }

  @Override
  public void onProviderEnabled(String provider) {

  }

  @Override
  public void onProviderDisabled(String provider) {

  }

  private void updateLocation(Location newLocation) {
    if (newLocation == null) {
      return;
    }
    if (currentBestLocation == null) {
      Log.d(LocationProvider.class.getSimpleName(), "Switched from null to " + newLocation.getLongitude() + ", " + newLocation.getLatitude() + ", " + newLocation.getAccuracy());
      currentBestLocation = newLocation;
    } else {
      boolean isMoreAccurate = newLocation.getAccuracy() < currentBestLocation.getAccuracy();
      boolean isSignificantlyNewer =
          (newLocation.getTime() - currentBestLocation.getTime()) / 1000 > 240;
      boolean isAccurate = newLocation.getAccuracy() < 50;
      if (isMoreAccurate || (isSignificantlyNewer && isAccurate)) {
        currentBestLocation = newLocation;
        Log.d(LocationProvider.class.getSimpleName(), "Switched from old Location to " + newLocation.getLongitude() + ", " + newLocation.getLatitude() + ", " + newLocation.getAccuracy());
      }
      long age = currentBestLocation.getTime() - System.currentTimeMillis();
      // if the best location lies has 30 meters of accuracy (with about 66% probability) and is
      // no older than two minute, accept is as a correct location
      if (currentBestLocation.getAccuracy() < 30 && age < 120000) {
        onAcquiredLocation();
      }
    }

  }

  private void onAcquiredLocation() {

    Log.d(LocationProvider.class.getSimpleName(), "Acquired final location, updating listeners");
    instanceLock.lock();
    hasAcquiredLocation = true;
    lastAcceptedLocation = currentBestLocation;
    locationManager.removeUpdates(this);
    for (OnLocationAcquiredListener l : onLocationAcquiredListeners) {
      l.OnLocationAcquired(currentBestLocation);
    }
    instance = null;
    instanceLock.unlock();
  }


  public interface OnLocationAcquiredListener {

    void OnLocationAcquired(Location location);
  }

}
