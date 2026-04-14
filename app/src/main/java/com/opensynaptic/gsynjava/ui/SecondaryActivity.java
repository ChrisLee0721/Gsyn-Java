package com.opensynaptic.gsynjava.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.opensynaptic.gsynjava.R;
import com.opensynaptic.gsynjava.databinding.ActivitySecondaryBinding;
import com.opensynaptic.gsynjava.ui.mirror.HealthMirrorFragment;
import com.opensynaptic.gsynjava.ui.mirror.HistoryMirrorFragment;
import com.opensynaptic.gsynjava.ui.mirror.MapMirrorFragment;
import com.opensynaptic.gsynjava.ui.mirror.RulesMirrorFragment;

public class SecondaryActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_HISTORY = "history";
    public static final String MODE_MAP = "map";
    public static final String MODE_RULES = "rules";
    public static final String MODE_HEALTH = "health";

    private ActivitySecondaryBinding binding;

    public static Intent intent(Context context, String mode, int titleRes) {
        return new Intent(context, SecondaryActivity.class)
                .putExtra(EXTRA_MODE, mode)
                .putExtra(Intent.EXTRA_TITLE, context.getString(titleRes));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySecondaryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);
        String title = getIntent().getStringExtra(Intent.EXTRA_TITLE);
        String mode = getIntent().getStringExtra(EXTRA_MODE);
        binding.toolbar.setTitle(title == null || title.isEmpty() ? getString(R.string.shell_title_details) : title);
        binding.toolbar.setSubtitle(subtitleFor(mode));
        binding.toolbar.setNavigationOnClickListener(v -> finish());
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.secondary_fragment_container, fragmentForMode(mode))
                    .commit();
        }
    }

    private Fragment fragmentForMode(String mode) {
        if (MODE_MAP.equals(mode)) return new MapMirrorFragment();
        if (MODE_RULES.equals(mode)) return new RulesMirrorFragment();
        if (MODE_HEALTH.equals(mode)) return new HealthMirrorFragment();
        return new HistoryMirrorFragment();
    }

    private CharSequence subtitleFor(String mode) {
        if (MODE_MAP.equals(mode)) return getString(R.string.shell_toolbar_subtitle_map);
        if (MODE_RULES.equals(mode)) return getString(R.string.shell_toolbar_subtitle_rules);
        if (MODE_HEALTH.equals(mode)) return getString(R.string.shell_toolbar_subtitle_health);
        return getString(R.string.shell_toolbar_subtitle_history);
    }
}

