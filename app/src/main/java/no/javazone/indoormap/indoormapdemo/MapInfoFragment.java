package no.javazone.indoormap.indoormapdemo;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import no.javazone.indoormap.indoormapdemo.maps.MarkerModel;
import no.javazone.indoormap.indoormapdemo.util.MapUtils;

public abstract class MapInfoFragment extends Fragment {


    private static final int QUERY_TOKEN_SESSION_ROOM = 0x1;
    private static final int QUERY_TOKEN_SUBTITLE = 0x2;
    private static final String QUERY_ARG_ROOMID = "roomid";
    private static final String QUERY_ARG_ROOMTITLE = "roomtitle";
    private static final String QUERY_ARG_ROOMTYPE = "roomicon";

    protected TextView mTitle;
    protected TextView mSubtitle;
    protected ImageView mIcon;

    protected ListView mList;

    protected Callback mCallback = sDummyCallback;

    private static Callback sDummyCallback = new Callback() {
        @Override
        public void onInfoSizeChanged(int left, int top, int right, int bottom) {
        }
    };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (!(activity instanceof Callback)) {
            throw new ClassCastException("Activity must implement fragment's callback.");
        }

        mCallback = (Callback) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallback = sDummyCallback;
    }

    @Nullable
    @Override
    public abstract View onCreateView(LayoutInflater inflater, ViewGroup container,
                                      Bundle savedInstanceState);

    @Nullable
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState, int layout) {
        View root = inflater.inflate(layout, container, false);

        mTitle = (TextView) root.findViewById(R.id.map_info_title);
        mSubtitle = (TextView) root.findViewById(R.id.map_info_subtitle);
        mIcon = (ImageView) root.findViewById(R.id.map_info_icon);
        mIcon.setColorFilter(getResources().getColor(R.color.my_schedule_icon_default));
        mList = (ListView) root.findViewById(R.id.map_info_list);
        return root;
    }

    public static MapInfoFragment newInstance(Context c) {
        return SlideableInfoFragment.newInstance();
    }

    /**
     * Called when the subtitle has been loaded for a room.
     */
    protected void onRoomSubtitleLoaded(String roomTitle, int roomType, String subTitle) {

    }

    protected void onSessionListLoading(String roomId, String roomTitle) {
        // No default behavior
    }

    /**
     * Prepares and starts a SessionLoader for the specified query token.
     */
    private void loadSessions(String roomId, String roomTitle, int roomType, int queryToken) {
        setHeader(MapUtils.getRoomIcon(roomType), roomTitle, null);

        if(queryToken == QUERY_TOKEN_SUBTITLE) {
            showSessionSubtitle(roomTitle, roomType);
        }


        // Load the following sessions for this room
        Bundle args = new Bundle();
        args.putString(QUERY_ARG_ROOMID, roomId);
        args.putString(QUERY_ARG_ROOMTITLE, roomTitle);
        args.putInt(QUERY_ARG_ROOMTYPE, roomType);
    }

    public void showFirstSessionTitle(String roomId, String roomTitle, int roomType) {
        loadSessions(roomId, roomTitle, roomType, QUERY_TOKEN_SUBTITLE);
    }

    private void showSessionSubtitle(String roomTitle, int roomType) {

        final String title = roomTitle;
        final String subtitle = "";

        setHeader(MapUtils.getRoomIcon(roomType), title, subtitle);
        mList.setVisibility(View.GONE);

        onRoomSubtitleLoaded(title, roomType, subtitle);
    }

    protected void onSessionsLoaded(String roomTitle, int roomType, Cursor cursor) {
        setHeader(MapUtils.getRoomIcon(roomType), roomTitle, null);
        mList.setVisibility(View.VISIBLE);
    }

    protected void onSessionLoadingFailed(String roomTitle, int roomType) {
        setHeader(MapUtils.getRoomIcon(roomType), roomTitle, null);
        mList.setVisibility(View.GONE);
    }

    public void showOsloSpektrum() {
        setHeader(MapUtils.getRoomIcon(MarkerModel.TYPE_OSLOSPEKTRUM), R.string.map_oslospektrum,
                R.string.map_spektrum_address);
        mList.setVisibility(View.GONE);
    }

    protected void setHeader(int icon, int title, int subTitle) {
        mIcon.setImageResource(icon);

        if (title != 0) {
            mTitle.setText(title);
            mTitle.setVisibility(View.VISIBLE);
        } else {
            mTitle.setVisibility(View.GONE);
        }

        if (subTitle != 0) {
            mSubtitle.setText(subTitle);
            mSubtitle.setVisibility(View.VISIBLE);
        } else {
            mSubtitle.setVisibility(View.GONE);
        }

    }

    private void setHeader(int icon, String title, String subTitle) {
        mIcon.setImageResource(icon);

        if (title != null && !title.isEmpty()) {
            mTitle.setText(title);
            mTitle.setVisibility(View.VISIBLE);
        } else {
            mTitle.setVisibility(View.GONE);
        }

        if (subTitle != null && !subTitle.isEmpty()) {
            mSubtitle.setText(subTitle);
            mSubtitle.setVisibility(View.VISIBLE);
        } else {
            mSubtitle.setVisibility(View.GONE);
        }
    }

    public void showTitleOnly(int roomType, String title) {
        setHeader(MapUtils.getRoomIcon(roomType), title, null);
        mList.setVisibility(View.GONE);
    }

    public abstract void hide();

    public abstract boolean isExpanded();

    public abstract void minimize();

    public interface Callback {

        public void onInfoSizeChanged(int left, int top, int right, int bottom);
    }
}