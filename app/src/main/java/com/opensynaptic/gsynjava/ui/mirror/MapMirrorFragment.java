package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.chip.ChipGroup;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.core.AppThemeConfig;
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

    private final Handler autoRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override public void run() {
            loadMarkers();
            autoRefreshHandler.postDelayed(this, 10_000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map_mirror, container, false);
        repository = AppController.get(requireContext()).repository();
        tvMapSummary = view.findViewById(R.id.tvMapSummary);

        view.findViewById(R.id.btnRefresh).setOnClickListener(v -> loadMarkers());

        ChipGroup chipGroup = view.findViewById(R.id.chipGroupMapType);
        chipGroup.setOnCheckedStateChangeListener((group, ids) -> {
            if (googleMap == null || ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipSatellite)   googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
            else if (id == R.id.chipHybrid) googleMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
            else                            googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        });

        // Use commitNow() so the fragment is attached before getMapAsync is called
        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentByTag("MAP_FRAG");
        if (mapFrag == null) {
            mapFrag = SupportMapFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.mapContainer, mapFrag, "MAP_FRAG")
                    .commitNow();
        }
        mapFrag.getMapAsync(this);
        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;

        // Apply dark map style when app uses dark background
        AppThemeConfig.BgPreset bg = AppThemeConfig.loadBgPreset(requireContext());
        if (!bg.isLight) {
            try {
                map.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                        requireContext(), R.raw.map_style_dark));
            } catch (Exception ignored) {}
        }

        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return true;
        });
        // Default view: China center, zoom 4 — visible even with no device data
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(35.0, 105.0), 4f));
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
        long onlineCount = 0;
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
        boolean hasBounds = false;

        for (Models.Device device : devices) {
            if ("online".equals(device.status)) onlineCount++;
            boolean hasGps = Math.abs(device.lat) > 1e-7 || Math.abs(device.lng) > 1e-7;
            if (!hasGps) continue;
            mappedCount++;

            LatLng pos = new LatLng(device.lat, device.lng);
            String title = (device.name == null || device.name.isEmpty())
                    ? "Device " + device.aid : device.name;
            boolean online = "online".equals(device.status);

            String snippet = getString(R.string.map_snippet_format,
                    device.aid,
                    online ? getString(R.string.map_status_online) : getString(R.string.map_status_offline),
                    UiFormatters.upperOrFallback(device.transportType, "UDP"),
                    UiFormatters.formatRelativeTime(device.lastSeenMs));

            float hue = online ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_ORANGE;
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos).title(title).snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
            if (marker != null) currentMarkers.add(marker);
            boundsBuilder.include(pos);
            hasBounds = true;
        }

        tvMapSummary.setText(getString(R.string.map_summary_format,
                devices.size(), onlineCount, mappedCount));

        if (!hasBounds) return;
        try {
            LatLngBounds bounds = boundsBuilder.build();
            if (mappedCount == 1) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(bounds.getCenter(), 14f));
            } else {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
            }
        } catch (Exception ignored) {}
    }
}
