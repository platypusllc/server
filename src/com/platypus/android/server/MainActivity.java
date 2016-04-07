package com.platypus.android.server;

import android.Manifest;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity that is used to start/stop/configure the vehicle server application.
 */
public class MainActivity extends Activity {

    protected ViewPager mPager;

    /**
     * Statically constructs a list of the fragments that are used by the pager.
     * The order of these entries determines the order that pages appear in the pager.
     *
     * @return list of fragments used by the view pager.
     */
    static List<Fragment> getFragments() {
        List<Fragment> fragmentList = new ArrayList<>();
        Bundle args = new Bundle();

        // Create each fragment with no arguments (empty bundle set).
        Fragment settingsFragment = new SettingsFragment();
        settingsFragment.setArguments(args);
        fragmentList.add(settingsFragment);

        Fragment launcherFragment = new LauncherFragment();
        launcherFragment.setArguments(args);
        fragmentList.add(launcherFragment);

        Fragment debugFragment = new DebugFragment();
        debugFragment.setArguments(args);
        fragmentList.add(debugFragment);

        return fragmentList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = (ViewPager) findViewById(R.id.pager);
        PagerAdapter pagerAdapter = new MainPageAdapter(getFragmentManager(), getFragments());
        mPager.setAdapter(pagerAdapter);

        // Start on the middle page (the launcher fragment).
        mPager.setCurrentItem(1);

        // Check for location permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            // Request location permissions
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
        }
    }

    /**
     * Implements a simple page adapter that cycles through several fragments.
     */
    static class MainPageAdapter extends FragmentPagerAdapter {
        private List<Fragment> mFragments;

        public MainPageAdapter(FragmentManager manager, List<Fragment> fragments) {
            super(manager);
            mFragments = fragments;
        }

        @Override
        public Fragment getItem(int position) {
            return mFragments.get(position);
        }

        @Override
        public int getCount() {
            return mFragments.size();
        }
    }

    /**
     * Overrides the back-button to *always* just go to the launcher screen.
     */
    @Override
    public void onBackPressed() {
        mPager.setCurrentItem(1);
    }
}
