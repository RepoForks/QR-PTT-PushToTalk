/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.terracom.qrpttbeta.channel;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.terracom.jumble.IJumbleService;
import com.terracom.jumble.model.Channel;
import com.terracom.jumble.model.User;
import com.terracom.qrpttbeta.Constants;
import com.terracom.qrpttbeta.R;
import com.terracom.qrpttbeta.service.QRPushToTalkService;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

public class ChannelSearchProvider extends ContentProvider {
	
	public static final String INTENT_DATA_CHANNEL = "channel";
	public static final String INTENT_DATA_USER = "user";

    private IJumbleService mService;
    private final Object mServiceLock = new Object();

	private ServiceConnection mConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = (IJumbleService) service;
            synchronized (mServiceLock) {
                mServiceLock.notify();
            }
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}
	};
	
	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public String getType(Uri uri) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean onCreate() {
		return true;
	}
	

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {	
		
		// Try to connect to the service. Wait for conn to establish.
		if(mService == null) {
			Intent serviceIntent = new Intent(getContext(), QRPushToTalkService.class);
			getContext().bindService(serviceIntent, mConn, 0);

            synchronized (mServiceLock) {
                try {
                    mServiceLock.wait(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if(mService == null) {
                    Log.v(Constants.TAG, "Failed to connect to service from search provider!");
                    return null;
                }
            }
		}
		
		String query = "";
		for(int x=0;x<selectionArgs.length;x++) {
			query += selectionArgs[x];
			if(x != selectionArgs.length-1)
				query += " ";
		}
		
		query = query.toLowerCase(Locale.getDefault());
		
		MatrixCursor cursor = new MatrixCursor(new String[] { "_ID", SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA, SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_ICON_1, SearchManager.SUGGEST_COLUMN_TEXT_2, SearchManager.SUGGEST_COLUMN_INTENT_DATA });

        List<Channel> channels;
        List<User> users;
        try {
            channels = channelSearch(mService.getRootChannel(), query);
            users = userSearch(mService.getRootChannel(), query);
        } catch (RemoteException e) {
            e.printStackTrace();
            return cursor;
        }

        for(int x=0;x<channels.size();x++) {
			Channel channel = channels.get(x);
            cursor.addRow(new Object[] { x, INTENT_DATA_CHANNEL, channel.getName(), R.drawable.ic_action_channels, getContext().getString(R.string.search_channel_users, channel.getSubchannelUserCount()), channel.getId() });
		}

		for(int x=0;x<users.size();x++) {
			User user = users.get(x);
            cursor.addRow(new Object[] { x, INTENT_DATA_USER, user.getName(), R.drawable.ic_action_user_dark, getContext().getString(R.string.user), user.getSession() });
		}
		
		return cursor;
	}

    /**
     * Recursively searches the channel tree for a user with a name containing the given string,
     * ignoring case.
     * @param root The channel to recursively search for users within.
     * @param str The string to match against the user's name. Case insensitive.
     * @return A list of users whose names contain str.
     */
    private List<User> userSearch(Channel root, String str) throws RemoteException {
        List<User> list = new LinkedList<User>();
        userSearch(root, str, list);
        return list;
    }

    /**
     * @see #userSearch(Channel,String)
     */
    private void userSearch(Channel root, String str, List<User> users) throws RemoteException {
        if (root == null) {
            return;
        }
        for (int uid : root.getUsers()) {
            User user = mService.getUser(uid);
            if (user != null && user.getName() != null
                    && user.getName().toLowerCase().contains(str.toLowerCase())) {
                users.add(user);
            }
        }
        for (int cid : root.getSubchannels()) {
            Channel channel = mService.getChannel(cid);
            userSearch(channel, str, users);
        }
    }

    /**
     * Recursively searches the channel tree for a channel with a name containing the given string,
     * ignoring case.
     * @param root The channel to recursively search for subchannels within.
     * @param str The string to match against the channel's name. Case insensitive.
     * @return A list of channels whose names contain str.
     */
    private List<Channel> channelSearch(Channel root, String str) throws RemoteException {
        List<Channel> list = new LinkedList<Channel>();
        channelSearch(root, str, list);
        return list;
    }

    /**
     * @see #channelSearch(Channel,String)
     */
    private void channelSearch(Channel root, String str, List<Channel> channels) throws RemoteException {
        if (root == null) {
            return;
        }

        if (root.getName().toLowerCase().contains(str.toLowerCase())) {
            channels.add(root);
        }

        for (int cid : root.getSubchannels()) {
            Channel channel = mService.getChannel(cid);
            channelSearch(channel, str, channels);
        }
    }

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] selectionArgs) {
		// TODO Auto-generated method stub
		return 0;
	}

}
