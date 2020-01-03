package com.hal9000.tourmania.ui.my_tours;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.hal9000.tourmania.AppUtils;
import com.hal9000.tourmania.MainActivity;
import com.hal9000.tourmania.MainActivityViewModel;
import com.hal9000.tourmania.R;
import com.hal9000.tourmania.SharedPrefUtils;
import com.hal9000.tourmania.model.TourWaypoint;
import com.hal9000.tourmania.rest_api.RestClient;
import com.hal9000.tourmania.rest_api.files_upload_download.FileDownloadImageObj;
import com.hal9000.tourmania.rest_api.files_upload_download.TourFileDownloadResponse;
import com.hal9000.tourmania.rest_api.files_upload_download.FileUploadDownloadService;
import com.hal9000.tourmania.rest_api.tours.ToursService;
import com.hal9000.tourmania.ui.ToursAdapter;
import com.hal9000.tourmania.database.AppDatabase;
import com.hal9000.tourmania.model.TourWithWpWithPaths;
import com.hal9000.tourmania.ui.create_tour.CreateTourFragment;
import com.hal9000.tourmania.ui.create_tour.CreateTourFragmentArgs;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

// Used for displaying cached tours - either user's own or favourite tours.
public class MyToursFragment extends Fragment {

    private MyToursViewModel myToursViewModel;
    private MainActivityViewModel activityViewModel;
    private RecyclerView recyclerView;
    private ToursAdapter mAdapter;
    private RecyclerView.LayoutManager layoutManager;
    private List<TourWithWpWithPaths> toursWithTourWps;
    private int currentFragmentId;
    private int checkingDetailsTourIndex = -1;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityViewModel = ViewModelProviders.of(requireActivity()).get(MainActivityViewModel.class);
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        myToursViewModel =
                ViewModelProviders.of(this).get(MyToursViewModel.class);
        View root = inflater.inflate(R.layout.fragment_my_tours, container, false);

        currentFragmentId = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment).getCurrentDestination().getId();
        //Log.d("crashTest", "currId = " + currId);
        //Log.d("crashTest", "nav_my_tours = " + R.id.nav_my_tours);
        //Log.d("crashTest", "nav_fav_tours = " + R.id.nav_fav_tours);
        if (currentFragmentId == R.id.nav_my_tours) {
            View fab = root.findViewById(R.id.fab);
            fab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //Navigation.findNavController(view).navigate(R.id.createTourFragment, null);
                    Navigation.findNavController(view).navigate(R.id.action_nav_my_tours_to_nav_nested_create_tour, null);
                    //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show();
                }
            });
            fab.setVisibility(View.VISIBLE);
        }

        if (toursWithTourWps != null && !toursWithTourWps.isEmpty() && checkingDetailsTourIndex != -1) {
            Bundle bundle = activityViewModel.getAndClearBundle(CreateTourFragment.class);
            if (bundle != null) {
                float newRatingVal = bundle.getFloat(CreateTourFragment.NEW_TOUR_RATING_VAL_BUNDLE_KEY, -1.0f);
                int newRatingCount = bundle.getInt(CreateTourFragment.NEW_TOUR_RATING_COUNT_BUNDLE_KEY, -1);
                if (newRatingVal != -1.0f && newRatingCount != -1) {
                    toursWithTourWps.get(checkingDetailsTourIndex).tour.setRateVal(newRatingVal);
                    toursWithTourWps.get(checkingDetailsTourIndex).tour.setRateCount(newRatingCount);
                    mAdapter.notifyItemChanged(checkingDetailsTourIndex);
                }
            }
        }

        Future future = loadToursFromRoomDb();
        try {
            future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        createRecyclerView(root);
        loadToursFromServerDb();
        return root;
    }

    private Future loadToursFromRoomDb() {
        // Currently does NOT handle additional waypoint pics (PicturePath / TourWpWithPicPaths)
        //Log.d("crashTest", "loadToursFromRoomDb()");
        return AppDatabase.databaseWriteExecutor.submit(new Runnable() {
            public void run() {
                //Log.d("crashTest", "run()");
                AppDatabase appDatabase = AppDatabase.getInstance(requireContext());
                //List<Tour> toursWithTourWps = AppDatabase.getInstance(requireContext()).tourDAO().getTours();
                if (currentFragmentId == R.id.nav_my_tours) {
                    toursWithTourWps = appDatabase.tourDAO().getMyToursWithTourWps();
                }
                else if (currentFragmentId == R.id.nav_fav_tours){
                    toursWithTourWps = appDatabase.tourDAO().getFavouriteToursWithTourWps();
                }

                //Log.d("crashTest", Integer.toString(toursWithTourWps.size()));
            }
        });
    }

    private void loadToursFromServerDb() {
        AppDatabase.databaseWriteExecutor.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        final AppDatabase appDatabase = AppDatabase.getInstance(requireContext());
                        //Log.d("crashTest", "loadToursFromServerDb serverMyTourIds in db : " + serverMyTourIds.size());
                        //Log.d("crashTest", "loadToursFromServerDb serverMyTourIds in db : " + serverMyTourIds.toString());
                        ToursService client = RestClient.createService(ToursService.class);
                        Call<List<TourWithWpWithPaths>> call = null;
                        if (currentFragmentId == R.id.nav_my_tours) {
                            List<String> serverMyTourIds = appDatabase.tourDAO().getServerMyTourIds(-1);
                            call = serverMyTourIds == null || serverMyTourIds.isEmpty() ?
                                    client.getUserTours(SharedPrefUtils.getDecryptedString(requireContext(), MainActivity.getUsernameKey())) :
                                    client.getUserTours(SharedPrefUtils.getDecryptedString(requireContext(), MainActivity.getUsernameKey()), serverMyTourIds);
                        }
                        else if (currentFragmentId == R.id.nav_fav_tours) {
                            List<String> serverMyTourIds = appDatabase.tourDAO().getServerFavTourIds(-1);
                            call = serverMyTourIds == null || serverMyTourIds.isEmpty() ?
                                    client.getUserFavTours(SharedPrefUtils.getDecryptedString(requireContext(), MainActivity.getUsernameKey())) :
                                    client.getUserFavTours(SharedPrefUtils.getDecryptedString(requireContext(), MainActivity.getUsernameKey()), serverMyTourIds);
                        }
                        call.enqueue(new Callback<List<TourWithWpWithPaths>>() {
                            @Override
                            public void onResponse(Call<List<TourWithWpWithPaths>> call, final Response<List<TourWithWpWithPaths>> response) {
                                if (response.isSuccessful()) {
                                    //Log.d("crashTest", "loadToursFromServerDb onResponse");
                                    if (response.body() != null) {
                                        List<TourWithWpWithPaths> missingToursWithTourWps = response.body();
                                        if (missingToursWithTourWps.size() > 0) {

                                            HashSet<Long> tourTitlesHashSet = new HashSet<>();
                                            for (TourWithWpWithPaths tourWithWpWithPaths : mAdapter.mDataset) {
                                                tourTitlesHashSet.add(tourWithWpWithPaths.tour.getModifiedAt());
                                            }
                                            Iterator<TourWithWpWithPaths> it = missingToursWithTourWps.iterator();
                                            while (it.hasNext()) {
                                                if (tourTitlesHashSet.contains(it.next().tour.getModifiedAt()))
                                                    it.remove();
                                            }

                                            if (currentFragmentId == R.id.nav_my_tours)
                                                AppUtils.saveToursToLocalDb(missingToursWithTourWps, requireContext(), AppUtils.TOUR_TYPE_MY_TOUR);
                                            else if (currentFragmentId == R.id.nav_fav_tours)
                                                AppUtils.saveToursToLocalDb(missingToursWithTourWps, requireContext(), AppUtils.TOUR_TYPE_FAV_TOUR);
                                            else
                                                AppUtils.saveToursToLocalDb(missingToursWithTourWps, requireContext(), null);

                                            int oldSize = mAdapter.mDataset.size();
                                            mAdapter.mDataset.addAll(missingToursWithTourWps);
                                            mAdapter.notifyItemRangeInserted(oldSize, missingToursWithTourWps.size());

                                            loadToursImagesFromServerDb(missingToursWithTourWps);
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onFailure(Call<List<TourWithWpWithPaths>> call, Throwable t) {
                                t.printStackTrace();
                                //Log.d("crashTest", "loadToursFromServerDb onFailure");
                            }
                        });
                    }
                });
    }

    private void loadToursImagesFromServerDb(List<TourWithWpWithPaths> missingToursWithTourWps) {
        List<String> missingTourIds = new ArrayList<>(missingToursWithTourWps.size());
        for (TourWithWpWithPaths tourWithWpWithPaths : missingToursWithTourWps) {
            missingTourIds.add(tourWithWpWithPaths.tour.getServerTourId());
        }
        FileUploadDownloadService client = RestClient.createService(FileUploadDownloadService.class);
        //Log.d("crashTest", "Missing tour: " + Integer.toString(missingTourIds.size()));
        Call<List<TourFileDownloadResponse>> call = client.downloadMultipleToursImagesFiles(missingTourIds, true);
        call.enqueue(new Callback<List<TourFileDownloadResponse>>() {
            @Override
            public void onResponse(Call<List<TourFileDownloadResponse>> call, Response<List<TourFileDownloadResponse>> response) {
                final List<TourFileDownloadResponse> res = response.body();
                if (res != null && res.size() > 0) {
                    try {
                        AppDatabase.databaseWriteExecutor.submit(
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        loadToursImagesFromServerDbProcessResponse(res);
                                    } catch (Exception e) { // IOException
                                        e.printStackTrace();
                                    }
                                }
                            });
                    } catch (Exception e) { // IOException
                        //Log.d("crashTest", "Unknown expection while reading file download response");
                        e.printStackTrace();
                    }
                }
                //Log.d("crashTest", "loadToursImagesFromServerDb onResponse");
            }

            @Override
            public void onFailure(Call<List<TourFileDownloadResponse>> call, Throwable t) {
                t.printStackTrace();
                //Log.d("crashTest", "loadToursImagesFromServerDb onFailure");
            }
        });
    }

    private void loadToursImagesFromServerDbProcessResponse(List<TourFileDownloadResponse> res) {
        final AppDatabase appDatabase = AppDatabase.getInstance(requireContext());
        //Log.d("crashTest", "Missing tour: " + Integer.toString(res.size()));
        // for each tour
        for (TourFileDownloadResponse tourFileDownloadResponse : res) {
            //Log.d("crashTest", tourFileDownloadResponse.tourServerId);
            TourWithWpWithPaths tourWithWpWithPaths = appDatabase.tourDAO().getTourByServerTourIds(tourFileDownloadResponse.tourServerId);
            tourWithWpWithPaths.getSortedTourWpsWithPicPaths();  // make sure waypoints have sorted order
            if (tourFileDownloadResponse.images != null) {
                int wpImgId = -1;
                // for each image in tour
                for (Map.Entry<String, FileDownloadImageObj> entry : tourFileDownloadResponse.images.entrySet()) {
                    FileDownloadImageObj fileDownloadImageObj = entry.getValue();
                    //Log.d("crashTest", entry.getKey() + " / " + entry.getValue());
                    if (fileDownloadImageObj.base64 != null && fileDownloadImageObj.mime != null) {
                        File file = AppUtils.saveImageFromBase64(requireContext(), fileDownloadImageObj.base64, fileDownloadImageObj.mime, null);
                        // process main tour image
                        if (entry.getKey().equals("0")) {
                            //Log.d("crashTest", "updating main tour image");
                            tourWithWpWithPaths.tour.setTourImgPath(file.toURI().toString());
                            appDatabase.tourDAO().updateTour(tourWithWpWithPaths.tour);
                            int i = 0;
                            for (TourWithWpWithPaths t : mAdapter.mDataset) {
                                //Log.d("crashTest", t.tour.getServerTourId());
                                if (t.tour.getServerTourId().equals(tourFileDownloadResponse.tourServerId)) {
                                    t.tour.setTourImgPath(file.toURI().toString());
                                    while (recyclerView.isComputingLayout());
                                    mAdapter.notifyItemChanged(i);
                                }
                                i++;
                            }
                        }
                        // process tour waypoints images
                        else {
                            //Log.d("crashTest", "updating tour waypoint image");
                            TourWaypoint tourWaypoint = tourWithWpWithPaths._tourWpsWithPicPaths.get(Integer.parseInt(entry.getKey()) - 1).tourWaypoint;
                            tourWaypoint.setMainImgPath(file.toURI().toString());
                            appDatabase.tourWaypointDAO().updateTourWp(tourWaypoint);
                        }
                    }
                    wpImgId++;
                }
            }
        }
    }

    private void createRecyclerView(final View root) {
        // Create recycler view
        recyclerView = (RecyclerView) root.findViewById(R.id.my_tours_recycler_view);
        recyclerView.setHasFixedSize(true);
        // use a linear layout manager
        layoutManager = new LinearLayoutManager(getContext());
        recyclerView.setLayoutManager(layoutManager);

        // specify an adapter
        mAdapter = new ToursAdapter(toursWithTourWps,
                new ToursAdapter.ToursAdapterCallback() {
                    @Override
                    public Context getContext() {
                        return requireContext();
                    }

                    @Override
                    public void navigateToViewTour(int position) {
                        checkingDetailsTourIndex = position;
                        Navigation.findNavController(requireView()).navigate(R.id.nav_nested_create_tour,
                                new CreateTourFragmentArgs.Builder().setTourId(toursWithTourWps.get(position).tour.getTourId()).build().toBundle());
                    }
                },
                currentFragmentId == R.id.nav_my_tours ? R.layout.tour_rec_view_row : R.layout.tour_search_rec_view_row);
        recyclerView.setAdapter(mAdapter);
    }
}