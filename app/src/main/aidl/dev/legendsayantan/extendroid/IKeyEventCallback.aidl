// IKeyEventCallback.aidl
package dev.legendsayantan.extendroid;

interface IKeyEventCallback {
    // json: { "action": int, "keyCode": int, "metaState": int }
    void onKeyEvent(String json);
}
