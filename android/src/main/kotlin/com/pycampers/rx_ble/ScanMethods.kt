package com.pycampers.rx_ble

import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.scan.ScanFilter
import com.polidea.rxandroidble2.scan.ScanSettings
import com.pycampers.plugin_scaffold.catchErrors
import com.pycampers.plugin_scaffold.trySend
import com.pycampers.plugin_scaffold.trySendThrowable
import dumpScanResult
import io.flutter.plugin.common.EventChannel.EventSink
import io.flutter.plugin.common.EventChannel.StreamHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.reactivex.disposables.Disposable

interface ScanInterface {
    fun stopScan(call: MethodCall, result: MethodChannel.Result)
}

class ScanMethods(val bleClient: RxBleClient) : ScanInterface, StreamHandler {
    var disposable: Disposable? = null
    var eventSink: EventSink? = null

    fun stopScan() {
        disposable?.dispose()
        disposable = null
        eventSink?.endOfStream()
        eventSink = null
    }

    fun startScan(scanSettings: ScanSettings, scanFilter: ScanFilter, eventSink: EventSink) {
        stopScan()

        disposable = bleClient.scanBleDevices(scanSettings, scanFilter)
            .doFinally { catchErrors(eventSink) { eventSink.endOfStream() } }
            .subscribe(
                {
                    trySend(eventSink) {
                        val device = it.bleDevice
                        val state = getDeviceState(device.macAddress)
                        state.bleDevice = device
                        dumpScanResult(it)
                    }
                },
                {
                    trySendThrowable(eventSink, it)
                }
            )

        this.eventSink = eventSink
    }

    override fun onListen(args: Any?, eventSink: EventSink) {
        val map = args as Map<*, *>
        val scanSettings = ScanSettings.Builder().setScanMode(map["scanMode"] as Int - 1).build()
        val filter = ScanFilter.Builder()
        (map["deviceId"] as String?)?.let { filter.setDeviceAddress(it) }
        (map["name"] as String?)?.let { filter.setDeviceName(it) }
        startScan(scanSettings, filter.build(), eventSink)
    }

    override fun onCancel(args: Any?) {
        stopScan()
    }

    override fun stopScan(call: MethodCall, result: MethodChannel.Result) {
        stopScan()
        result.success(null)
    }
}