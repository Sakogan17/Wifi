package com.ejemplo.visorwifi;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends Activity {

    private WebView webView;
    private WifiManager wifi;
    private ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callbackActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.addJavascriptInterface(new Puente(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);

        pedirPermisos();
    }

    private void pedirPermisos() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            String[] permisos = Build.VERSION.SDK_INT >= 33
                    ? new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                   Manifest.permission.NEARBY_WIFI_DEVICES}
                    : new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
            requestPermissions(permisos, 1);
        }
    }

    private void avisarWeb(final String funcion, final String mensaje) {
        webView.post(() -> webView.evaluateJavascript(
                funcion + "(" + JSONObject.quote(mensaje) + ")", null));
    }

    private class Puente {

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public String escanear() {
            try {
                wifi.startScan();
                List<ScanResult> lista = wifi.getScanResults();
                JSONArray arr = new JSONArray();
                for (ScanResult r : lista) {
                    JSONObject o = new JSONObject();
                    o.put("ssid", r.SSID == null || r.SSID.isEmpty() ? "(red oculta)" : r.SSID);
                    o.put("bssid", r.BSSID);
                    o.put("dbm", r.level);
                    o.put("canal", canal(r.frequency));
                    o.put("banda", r.frequency > 4900 ? "5 GHz" : "2.4 GHz");
                    o.put("seguridad", seguridad(r.capabilities));
                    arr.put(o);
                }
                return arr.toString();
            } catch (SecurityException e) {
                return "SIN_PERMISO";
            } catch (Exception e) {
                return "[]";
            }
        }

        /** Intenta conectarse. El resultado vuelve a la página con resultadoConexion(texto). */
        @JavascriptInterface
        public void conectar(String ssid, String clave, String seguridad) {
            try {
                if (callbackActual != null) {
                    try { cm.unregisterNetworkCallback(callbackActual); } catch (Exception ignorar) {}
                    callbackActual = null;
                }

                WifiNetworkSpecifier.Builder b = new WifiNetworkSpecifier.Builder().setSsid(ssid);
                if ("WPA3".equals(seguridad)) {
                    b.setWpa3Passphrase(clave);
                } else {
                    b.setWpa2Passphrase(clave);
                }

                NetworkRequest pedido = new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                        .setNetworkSpecifier(b.build())
                        .build();

                callbackActual = new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network red) {
                        cm.bindProcessToNetwork(red);
                        avisarWeb("resultadoConexion", "OK");
                    }
                    @Override
                    public void onUnavailable() {
                        avisarWeb("resultadoConexion",
                                "No se pudo conectar. Revisa la contraseña o acércate al router.");
                    }
                    @Override
                    public void onLost(Network red) {
                        avisarWeb("resultadoConexion", "Se perdió la conexión.");
                    }
                };
                cm.requestNetwork(pedido, callbackActual, 30000);
            } catch (Exception e) {
                avisarWeb("resultadoConexion", "Error: " + e.getMessage());
            }
        }
    }

    private int canal(int f) {
        if (f == 2484) return 14;
        if (f < 2484) return (f - 2407) / 5;
        if (f >= 5000) return (f - 5000) / 5;
        return 0;
    }

    private String seguridad(String c) {
        if (c.contains("SAE")) return "WPA3";
        if (c.contains("WPA2") || c.contains("RSN")) return "WPA2";
        if (c.contains("WPA")) return "WPA";
        if (c.contains("WEP")) return "WEP";
        return "Abierta";
    }
}
