package com.opensynaptic.gsynjava.ui;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;
import com.opensynaptic.gsynjava.AppController;
import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.core.AppThemeConfig;
import com.opensynaptic.gsynjava.databinding.ActivityMainBinding;
import com.opensynaptic.gsynjava.ui.alerts.AlertsFragment;
import com.opensynaptic.gsynjava.ui.dashboard.DashboardFragment;
import com.opensynaptic.gsynjava.ui.devices.DevicesFragment;
import com.opensynaptic.gsynjava.ui.mirror.HealthMirrorFragment;
import com.opensynaptic.gsynjava.ui.mirror.HistoryMirrorFragment;
import com.opensynaptic.gsynjava.ui.mirror.MapMirrorFragment;
import com.opensynaptic.gsynjava.ui.mirror.RulesMirrorFragment;
import com.opensynaptic.gsynjava.ui.send.SendFragment;
import com.opensynaptic.gsynjava.ui.settings.SettingsFragment;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {
    private ActivityMainBinding binding;
    private ActionBarDrawerToggle drawerToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme overlays BEFORE super.onCreate() — this is the canonical approach.
        // AppCompat/Material3 reads theme attributes during super.onCreate(); overlays
        // applied before that call are guaranteed to be in place for all component setup.
        // getApplicationContext() is always safe here (Application is initialised before any Activity).
        getTheme().applyStyle(AppThemeConfig.getAccentOverlayRes(
                AppThemeConfig.loadThemePreset(getApplicationContext())), true);
        getTheme().applyStyle(AppThemeConfig.getBgOverlayRes(
                AppThemeConfig.loadBgPreset(getApplicationContext())), true);
        super.onCreate(savedInstanceState);
        AppController.get(this);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // Sync status-bar / nav-bar colours AFTER setContentView so the window is fully set up.
        AppThemeConfig.applyBgToWindow(getWindow(), this);
        setSupportActionBar(binding.toolbar);

        // ── Drawer toggle (hamburger icon) ──────────────────────────────────
        drawerToggle = new ActionBarDrawerToggle(
                this, binding.drawerLayout, binding.toolbar,
                R.string.nav_drawer_open, R.string.nav_drawer_close);
        binding.drawerLayout.addDrawerListener(drawerToggle);
        drawerToggle.syncState();

        binding.navView.setNavigationItemSelectedListener(this);

        // ── Bottom nav ──────────────────────────────────────────────────────
        binding.bottomNav.setOnItemSelectedListener(this::onBottomNavSelected);
        if (savedInstanceState == null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_dashboard);
            binding.navView.setCheckedItem(R.id.nav_main_dashboard);
        }
    }

    // ── Bottom navigation ────────────────────────────────────────────────────
    private boolean onBottomNavSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Fragment fragment;
        String title;
        String subtitle;

        if (id == R.id.nav_devices) {
            fragment = new DevicesFragment();
            title = getString(R.string.nav_devices);
            subtitle = getString(R.string.shell_toolbar_subtitle_devices);
            binding.navView.setCheckedItem(R.id.nav_main_devices);
        } else if (id == R.id.nav_alerts) {
            fragment = new AlertsFragment();
            title = getString(R.string.nav_alerts);
            subtitle = getString(R.string.shell_toolbar_subtitle_alerts);
            binding.navView.setCheckedItem(R.id.nav_main_alerts);
        } else if (id == R.id.nav_send) {
            fragment = new SendFragment();
            title = getString(R.string.nav_send);
            subtitle = getString(R.string.shell_toolbar_subtitle_send);
            binding.navView.setCheckedItem(R.id.nav_main_send);
        } else if (id == R.id.nav_settings) {
            fragment = new SettingsFragment();
            title = getString(R.string.nav_settings);
            subtitle = getString(R.string.shell_toolbar_subtitle_settings);
            binding.navView.setCheckedItem(R.id.nav_main_settings);
        } else {
            fragment = new DashboardFragment();
            title = getString(R.string.nav_dashboard);
            subtitle = getString(R.string.shell_toolbar_subtitle_dashboard);
            binding.navView.setCheckedItem(R.id.nav_main_dashboard);
        }
        showFragment(fragment, title, subtitle);
        return true;
    }

    // ── Side drawer navigation ────────────────────────────────────────────────
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        Fragment fragment = null;
        String title = null;
        String subtitle = null;

        if (id == R.id.nav_main_dashboard) {
            binding.bottomNav.setSelectedItemId(R.id.nav_dashboard);
        } else if (id == R.id.nav_main_devices) {
            binding.bottomNav.setSelectedItemId(R.id.nav_devices);
        } else if (id == R.id.nav_main_alerts) {
            binding.bottomNav.setSelectedItemId(R.id.nav_alerts);
        } else if (id == R.id.nav_main_send) {
            binding.bottomNav.setSelectedItemId(R.id.nav_send);
        } else if (id == R.id.nav_main_settings) {
            binding.bottomNav.setSelectedItemId(R.id.nav_settings);
        } else if (id == R.id.nav_drawer_map) {
            fragment = new MapMirrorFragment();
            title = getString(R.string.title_map);
            subtitle = getString(R.string.shell_toolbar_subtitle_map);
        } else if (id == R.id.nav_drawer_history) {
            fragment = new HistoryMirrorFragment();
            title = getString(R.string.title_history);
            subtitle = getString(R.string.shell_toolbar_subtitle_history);
        } else if (id == R.id.nav_drawer_rules) {
            fragment = new RulesMirrorFragment();
            title = getString(R.string.title_rules);
            subtitle = getString(R.string.shell_toolbar_subtitle_rules);
        } else if (id == R.id.nav_drawer_health) {
            fragment = new HealthMirrorFragment();
            title = getString(R.string.title_health);
            subtitle = getString(R.string.shell_toolbar_subtitle_health);
        }

        if (fragment != null) {
            // Deselect bottom nav when showing drawer page
            binding.bottomNav.getMenu().setGroupCheckable(0, true, false);
            for (int i = 0; i < binding.bottomNav.getMenu().size(); i++) {
                binding.bottomNav.getMenu().getItem(i).setChecked(false);
            }
            binding.bottomNav.getMenu().setGroupCheckable(0, true, true);
            showFragment(fragment, title, subtitle);
        }

        binding.drawerLayout.closeDrawers();
        return true;
    }

    private void showFragment(Fragment fragment, String title, String subtitle) {
        binding.toolbar.setTitle(title);
        binding.toolbar.setSubtitle(subtitle);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
