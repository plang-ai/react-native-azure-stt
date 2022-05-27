package com.reactnativeazurestt;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.speech.RecognitionService;
import android.util.Log;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.module.annotations.ReactModule;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.modules.core.PermissionAwareActivity;
import com.microsoft.cognitiveservices.speech.SpeechConfig;
import com.microsoft.cognitiveservices.speech.SpeechRecognizer;
import com.microsoft.cognitiveservices.speech.audio.AudioConfig;

import java.util.List;

import javax.annotation.Nullable;

@ReactModule(name = AzureSttModule.NAME)
public class AzureSttModule extends ReactContextBaseJavaModule {
  public static final String NAME = "AzureStt";
  final String logTag = "AzureStt";
  final ReactApplicationContext reactContext;
  private boolean isRecognizing = false;
  private MicrophoneStream microphoneStream;
  private SpeechRecognizer speech;
  private MicrophoneStream createMicrophoneStream() {
    this.releaseMicrophoneStream();

    microphoneStream = new MicrophoneStream();
    return microphoneStream;
  }
  private void releaseMicrophoneStream() {
    if (microphoneStream != null) {
      microphoneStream.close();
      microphoneStream = null;
    }
  }

  public AzureSttModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.reactContext = reactContext;
  }

  private void teardown() {
    this.releaseMicrophoneStream();
    if (speech != null) {
      speech.stopContinuousRecognitionAsync();
      speech = null;
    }
  }

  private void startListening(String sub) {
    this.teardown();
    try {
      final AudioConfig audioInput = AudioConfig.fromStreamInput(createMicrophoneStream());
      SpeechConfig speechConfig = SpeechConfig.fromSubscription(sub, "koreacentral");
      speechConfig.setSpeechSynthesisLanguage("en-US");
      speech = new SpeechRecognizer(speechConfig, audioInput);

      speech.recognized.addEventListener((o, speechRecognitionResultEventArgs) -> {
        final String s = speechRecognitionResultEventArgs.getResult().getText();
        if (s.isEmpty()) {
          this.teardown();
          return;
        }
        Log.i(logTag, "Final result: " + s);
        WritableArray arr = Arguments.createArray();
        arr.pushString(s);
        WritableMap event = Arguments.createMap();
        event.putArray("value", arr);
        sendEvent("onSpeechResults", event);
        Log.d(logTag, "onResults()");

        this.teardown();
      });

      speech.sessionStopped.addEventListener((o, s) -> {
        Log.i(logTag, "Session stopped.");
        this.teardown();
      });

      speech.sessionStarted.addEventListener((o, s) -> {
        WritableMap event = Arguments.createMap();
        event.putBoolean("error", false);
        sendEvent("onSpeechStart", event);
        Log.d(logTag, "onBeginningOfSpeech()");
      });

      speech.recognizing.addEventListener((o, speechRecognitionResultEventArgs) -> {
        final String s = speechRecognitionResultEventArgs.getResult().getText();
        WritableArray arr = Arguments.createArray();
        arr.pushString(s);
        WritableMap event = Arguments.createMap();
        event.putArray("value", arr);
        sendEvent("onSpeechPartialResults", event);
        Log.d(logTag, "onPartialResults()");
      });

      speech.canceled.addEventListener((o, s) -> {
        this.teardown();
      });

      speech.sessionStopped.addEventListener((o, s) -> {
        WritableMap event = Arguments.createMap();
        event.putBoolean("error", false);
        sendEvent("onSpeechEnd", event);
        Log.d(logTag, "onEndOfSpeech()");
        isRecognizing = false;

        this.teardown();
      });

      speech.startContinuousRecognitionAsync();
    } catch (Exception ex) {
      System.out.println(ex.getMessage());
      String errorMessage = "recognition failed";
      WritableMap error = Arguments.createMap();
      error.putString("message", errorMessage);
      WritableMap event = Arguments.createMap();
      event.putMap("error", error);
      sendEvent("onSpeechError", event);
      Log.d(logTag, "onError() - " + errorMessage);
    }
  }

  private void startSpeechWithPermissions(final String sub) {
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(() -> {
      try {
        startListening(sub);
        isRecognizing = true;
      } catch (Exception e) {
        String errorMessage = "recognition error";
        WritableMap error = Arguments.createMap();
        error.putString("message", errorMessage);
        WritableMap event = Arguments.createMap();
        event.putMap("error", error);
        sendEvent("onSpeechError", event);
        Log.d(logTag, "onError() - " + errorMessage);
      }
    });
  }

  @ReactMethod
  public void startSpeech(final String sub) {
    if (!isPermissionGranted()) {
      String[] PERMISSIONS = {Manifest.permission.RECORD_AUDIO};
      if (this.getCurrentActivity() != null) {
        ((PermissionAwareActivity) this.getCurrentActivity()).requestPermissions(PERMISSIONS, 1, (requestCode, permissions, grantResults) -> {
          boolean permissionsGranted = true;
          for (int i = 0; i < permissions.length; i++) {
            final boolean granted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
            permissionsGranted = permissionsGranted && granted;
          }
          startSpeechWithPermissions(sub);
          return permissionsGranted;
        });
      }
      return;
    }
    startSpeechWithPermissions(sub);
  }

  @ReactMethod
  public void stopSpeech() {
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(() -> {
      if (speech != null) {
        this.teardown();
      }
      isRecognizing = false;
    });
  }

  @ReactMethod
  public void cancelSpeech() {
    if (speech != null) {
      this.teardown();
    }
    isRecognizing = false;
  }

  @ReactMethod
  public void destroySpeech(final Callback callback) {
    Handler mainHandler = new Handler(this.reactContext.getMainLooper());
    mainHandler.post(() -> {
      try {
        this.teardown();
        isRecognizing = false;
        callback.invoke(false);
      } catch(Exception e) {
        callback.invoke(e.getMessage());
      }
    });
  }

  @ReactMethod
  public void isSpeechAvailable(final Callback callback) {
    callback.invoke(true);
  }

  @ReactMethod
  public void getSpeechRecognitionServices(Promise promise) {
    final List<ResolveInfo> services = this.reactContext.getPackageManager()
      .queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
    WritableArray serviceNames = Arguments.createArray();
    for (ResolveInfo service : services) {
      serviceNames.pushString(service.serviceInfo.packageName);
    }

    promise.resolve(serviceNames);
  }

  private boolean isPermissionGranted() {
    String permission = Manifest.permission.RECORD_AUDIO;
    int res = getReactApplicationContext().checkCallingOrSelfPermission(permission);
    return res == PackageManager.PERMISSION_GRANTED;
  }

  @ReactMethod
  public void isRecognizing(Callback callback) {
    callback.invoke(isRecognizing);
  }

  private void sendEvent(String eventName, @Nullable WritableMap params) {
    this.reactContext
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
      .emit(eventName, params);
  }

  @NonNull
  @Override
  public String getName() {
    return NAME;
  }
}
