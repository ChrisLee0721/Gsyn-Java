package com.opensynaptic.gsynjava.ui;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.databinding.ActivityMainBinding;
import com.opensynaptic.gsynjava.ui.alerts.AlertsFragment;
import com.opensynaptic.gsynjava.ui.dashboard.DashboardFragment;
import com.opensynaptic.gsynjava.ui.devices.DevicesFragment;
import com.opensynaptic.gsynjava.ui.send.SendFragment;
import com.opensynaptic.gsynjava.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppController.get(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        binding.bottomNav.setOnItemSelectedListener(this::onNavSelected);
        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    }

    private boolean onNavSelected(@NonNull android.view.MenuItem item) {
        Fragment fragment;
        String title;
        int id = item.getItemId();
        if (id == R.id.nav_devices) {
            fragment = new DevicesFragment();
            title = getString(R.string.nav_devices);
        } else if (id == R.id.nav_alerts) {
            fragment = new AlertsFragment();
            title = getString(R.string.nav_alerts);
        } else if (id == R.id.nav_send) {
            fragment = new SendFragment();
            title = getString(R.string.nav_send);
        } else if (id == R.id.nav_settings) {
            fragment = new SettingsFragment();
            title = getString(R.string.nav_settings);
        } else {
            fragment = new DashboardFragment();
            title = getString(R.string.nav_dashboard);
        }
        binding.toolbar.setTitle(title);
        binding.toolbar.setSubtitle(subtitleFor(id));
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
        return true;
    }

    private CharSequence subtitleFor(int id) {
        if (id == R.id.nav_devices) return getString(R.string.shell_toolbar_subtitle_devices);
        if (id == R.id.nav_alerts) return getString(R.string.shell_toolbar_subtitle_alerts);
        if (id == R.id.nav_send) return getString(R.string.shell_toolbar_subtitle_send);
        if (id == R.id.nav_settings) return getString(R.string.shell_toolbar_subtitle_settings);
        return getString(R.string.shell_toolbar_subtitle_dashboard);
    }
}

