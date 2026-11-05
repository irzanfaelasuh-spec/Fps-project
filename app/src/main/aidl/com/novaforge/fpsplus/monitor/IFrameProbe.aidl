package com.novaforge.fpsplus.monitor;

interface IFrameProbe {
    String getTopPackage();
    String getLayerList();
    String getLatency(String layerName);
    String getThermalDump();
}
