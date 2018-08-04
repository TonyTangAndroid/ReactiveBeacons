/*
 * Copyright (C) 2015 Piotr Wittchen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pwittchen.reactivebeacons.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.pwittchen.reactivebeacons.R;
import com.github.pwittchen.reactivebeacons.library.rx2.Beacon;
import com.github.pwittchen.reactivebeacons.library.rx2.Proximity;
import com.github.pwittchen.reactivebeacons.library.rx2.ReactiveBeacons;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.disposables.Disposable;

import static io.reactivex.android.schedulers.AndroidSchedulers.mainThread;
import static io.reactivex.schedulers.Schedulers.computation;

public class MainActivity extends Activity {


    private static final String ITEM_FORMAT = "MAC: %s, RSSI: %d\ndistance: %.2fm, proximity: %s\n%s";
    private ReactiveBeacons reactiveBeacons;
    private Disposable subscription;
    private Map<String, Beacon> beacons;
    private MyAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        reactiveBeacons = new ReactiveBeacons(this);
        beacons = new HashMap<>();
    }

    private void initView() {
        RecyclerView recyclerView = findViewById(R.id.rv_beacons);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyAdapter();
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setAdapter(adapter);
        List<Beacon> emptyList = new ArrayList<>();
        adapter.setBeacons(emptyList);
    }

    @Override
    protected void onResume() {
        super.onResume();
        startSubscription();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }


    private void startSubscription() {

        subscription = reactiveBeacons.observe()
//                .filter(this::near)
//                .filter(this::fixedBeacon)
                .map(this::toListResult)
                .scan(Pair.create(new ArrayList<>(), null), this::toPair)
                .skip(1)
                .subscribeOn(computation())
                .observeOn(mainThread())
                .subscribe(this::refreshUI);
    }

    private boolean fixedBeacon(Beacon beacon) {
        return beacon.macAddress.address.endsWith("75:EE");
    }


    private boolean near(Beacon beacon) {
        return beacon.getProximity().equals(Proximity.IMMEDIATE);
    }

    private void refreshUI(Pair<List<Beacon>, DiffUtil.DiffResult> listDiffResultPair) {
        adapter.setBeacons(listDiffResultPair.first);
        DiffUtil.DiffResult second = listDiffResultPair.second;
        if (second != null)
            second.dispatchUpdatesTo(adapter);
    }

    @NonNull
    private Pair<List<Beacon>, DiffUtil.DiffResult> toPair(Pair<List<Beacon>, DiffUtil.DiffResult> pair, List<Beacon> next) {
        MyDiffCallback callback = new MyDiffCallback(pair.first, next);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(callback);
        return Pair.create(next, result);
    }

    private List<Beacon> toListResult(Beacon beacon) {
        beacons.put(beacon.device.getAddress(), beacon);
        return new ArrayList<>(beacons.values());
    }


    @SuppressLint("DefaultLocale")
    private static String getBeaconItemString(Beacon beacon) {
        String mac = beacon.device.getAddress();
        int rssi = beacon.rssi;
        double distance = beacon.getDistance();
        Proximity proximity = beacon.getProximity();
        String name = beacon.device.getName();
        return String.format(ITEM_FORMAT, mac, rssi, distance, proximity, name);
    }


    private static class MyAdapter extends RecyclerView.Adapter<BeaconViewHolder> {
        private List<Beacon> things = new ArrayList<>(); // Start with empty list

        @android.support.annotation.NonNull
        @Override
        public BeaconViewHolder onCreateViewHolder(@android.support.annotation.NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_beacon, parent, false);
            return new BeaconViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@android.support.annotation.NonNull BeaconViewHolder holder, int position) {
            Beacon thing = things.get(position);
            holder.bind(thing);
        }

        @Override
        public int getItemCount() {
            return things.size();
        }

        public void setBeacons(List<Beacon> things) {
            this.things = things;
        }
    }

    private static class BeaconViewHolder extends RecyclerView.ViewHolder {

        private final TextView textView;

        public BeaconViewHolder(View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.text);
        }

        public void bind(Beacon thing) {
            textView.setText(getBeaconItemString(thing));
        }
    }

    private static class MyDiffCallback extends DiffUtil.Callback {
        private List<Beacon> current;
        private List<Beacon> next;

        public MyDiffCallback(List<Beacon> current, List<Beacon> next) {
            this.current = current;
            this.next = next;
        }

        @Override
        public int getOldListSize() {
            return current.size();
        }

        @Override
        public int getNewListSize() {
            return next.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            Beacon currentItem = current.get(oldItemPosition);
            Beacon nextItem = next.get(newItemPosition);
            return currentItem.device.getAddress().equals(nextItem.device.getAddress());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            Beacon currentItem = current.get(oldItemPosition);
            Beacon nextItem = next.get(newItemPosition);
            return currentItem.toString().equals(nextItem.toString());
        }
    }
}
