package com.opensynaptic.gsynjava.ui.mirror;

import android.os.Bundle;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map_mirror, container, false);
        repository = AppController.get(requireContext()).repository();
        tvMapSummary = view.findViewById(R.id.tvMapSummary);

        MaterialButton btnRefresh = view.findViewById(R.id.btnRefresh);
        btnRefresh.setOnClickListener(v -> loadMarkers());

        // Obtain the SupportMapFragment and get notified when the map is ready
        SupportMapFragment mapFrag = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFrag != null) {
            mapFrag.getMapAsync(this);
        }
        return view;
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        this.googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.setOnMarkerClickListener(marker -> {
            Toast.makeText(requireContext(), marker.getTitle() + "\n" + marker.getSnippet(),
                    Toast.LENGTH_SHORT).show();
            return true;
        });
        loadMarkers();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (googleMap != null) loadMarkers();
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
            String snippet = String.format(Locale.getDefault(),
                    "AID %d · %s · %s",
                    device.aid,
                    UiFormatters.upperOrFallback(device.transportType, "UDP"),
                    UiFormatters.formatRelativeTime(device.lastSeenMs));

            boolean online = "online".equals(device.status);
            float hue = online ? BitmapDescriptorFactory.HUE_GREEN : BitmapDescriptorFactory.HUE_ORANGE;

            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(snippet)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue)));
            if (marker != null) currentMarkers.add(marker);
            boundsBuilder.include(pos);
            hasBounds = true;
        }

        // Update summary bar
        tvMapSummary.setText(String.format(Locale.getDefault(),
                "设备 %d 台 · 已定位 %d 台", devices.size(), mappedCount));

        if (hasBounds) {
            try {
                LatLngBounds bounds = boundsBuilder.build();
                if (mappedCount == 1) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            bounds.getCenter(), 14f));
                } else {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120));
                }
            } catch (Exception ignored) {}
        }
    }
}
