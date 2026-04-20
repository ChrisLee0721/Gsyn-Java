package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.data.AppRepository;
import com.opensynaptic.gsynjava.data.Models;
import com.opensynaptic.gsynjava.ui.common.UiFormatters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapMirrorFragment extends Fragment implements OnMapReadyCallback {

    private AppRepository repository;
    private GoogleMap googleMap;
    private TextView tvMapSummary;
    private final List<Marker> currentMarkers = new ArrayList<>();

    // Auto-refresh every 10 seconds while visible
    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override public void run() {
            loadMarkers();
            autoRefreshHandler.postDelayed(this, 10_000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map_mirror, container, false);
        repository = AppController.get(requireContext()).repository();
        tvMapSummary = view.findViewById(R.id.tvMapSummary);

        // Refresh button
        MaterialButton btnRefresh = view.findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(v -> loadMarkers());

        // Map type chips
        ChipGroup chipGroup = view.findViewById(R.id.chipGroupMapType);
        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (googleMap == null || checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            if (id == R.id.chipSatellite) googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            else if (id == R.id.chipHybrid) googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            else googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        });

        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFrag != null) mapFrag.getMapAsync(this);
        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setInfoWindowAdapter(null); // use default info window
        googleMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return true;
        });
        loadMarkers();
    }

    @Override
    public void onStart() {
        super.onStart();
        autoRefreshHandler.post(autoRefreshRunnable);
    }

    @Override
    public void onStop() {
        super.onStop();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    private void loadMarkers() {
        if (googleMap == null) return;
        googleMap.clear();
        currentMarkers.clear();

        List<Models.Device> devices = repository.getAllDevices();
        int mappedCount = 0;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasBounds = false;

        for (Models.Device device : devices) {
            boolean hasGps = Math.abs(device.lat) > 1e-7 || Math.abs(device.lng) > 1e-7;
            if (!hasGps) continue;
            mappedCount++;

            LatLng pos = new LatLng(device.lat, device.lng);
            String title = (device.name == null || device.name.isEmpty())
                    ? "Device " + device.aid : device.name;
            boolean online = "online".equals(device.status);
            String snippet = String.format(Locale.getDefault(),
                    "%s  |  AID %d  |  %s\n最后在线: %s",
                    online ? "🟢 在线" : "🟠 离线",
                    device.aid,
                    UiFormatters.upperOrFallback(device.transportType, "UDP"),
                    UiFormatters.formatRelativeTime(device.lastSeenMs));

            float hue = online
                    ? BitmapDescriptorFactory.HUE_GREEN
                    : BitmapDescriptorFactory.HUE_ORANGE;

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
            if (marker != null) currentMarkers.add(marker);
            boundsBuilder.include(pos);
            hasBounds = true;
        }

        // Summary bar
        long onlineCount = devices.stream().filter(d -> "online".equals(d.status)).count();
        tvMapSummary.setText(String.format(Locale.getDefault(),
                "共 %d 台 · 在线 %d · 已定位 %d · 自动刷新 10s",
                devices.size(), onlineCount, mappedCount));

        if (hasBounds) {
            try {
                LatLngBounds bounds = boundsBuilder.build();
                if (mappedCount == 1) {
                    googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 14f));
                } else {
                    googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(bounds, 120));
                }
            } catch (Exception ignored) {}
        }
    }
}
