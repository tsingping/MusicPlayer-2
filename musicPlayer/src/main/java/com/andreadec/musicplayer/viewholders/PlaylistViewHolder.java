/*
 * Copyright 2015-2019 Andrea De Cesare
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.andreadec.musicplayer.viewholders;

import androidx.recyclerview.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.andreadec.musicplayer.*;
import com.andreadec.musicplayer.models.*;

public class PlaylistViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
    private MainActivity activity;
    private ListsClickListener clickListener;
    private Playlist playlist;
    private TextView name;
    private ImageButton menu;

    public PlaylistViewHolder(View view, MainActivity activity, final ListsClickListener clickListener) {
        super(view);
        this.activity = activity;
        this.clickListener = clickListener;
        name = view.findViewById(R.id.textViewName);
        menu = view.findViewById(R.id.buttonMenu);
        view.setOnClickListener(this);
        menu.setOnClickListener(this);
        menu.setFocusable(false);
    }

    public void update(Playlist playlist) {
        this.playlist = playlist;
        name.setText(playlist.getName());
    }

    @Override
    public void onClick(View view) {
        if(view.equals(menu)) {
            final PopupMenu popup = new PopupMenu(activity, menu);
            popup.getMenuInflater().inflate(R.menu.contextmenu_editdelete, popup.getMenu());
            popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                public boolean onMenuItemClick(MenuItem item) {
                    clickListener.onCategoryMenuClick(playlist, item.getItemId());
                    return true;
                }
            });
            popup.show();
        } else {
            clickListener.onCategoryClick(playlist);
        }
    }
}
