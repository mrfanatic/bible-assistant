/*
 * Copyright (C) 2011 The Bible Assistant Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.heavenus.bible.assistant;

import java.util.ArrayList;
import java.util.List;

import org.heavenus.bible.provider.BibleStore;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class BookContentActivity extends BaseActivity implements ListView.OnItemClickListener {
	static final String EXTRA_BOOK_TITLE = "org.heavenus.bible.assistant.intent.extra.BOOK_TITLE"; // String

	private ListView mSectionListView;
	
	private class Section {
		public String name;
		public String content;
		
		public String chapterId;
		public int sectionId;

		public boolean isTitle;
		public boolean isMainTitle;
		public boolean isChapterTitle;
	}
	private Uri mBookUri;
	private String mBookTitle;
	private String mBookMarkSection;
	private int mBookMarkSectionPos = -1;

	private List<Section> mSections;
	private SectionAdapter mSectionAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.book_content);
        mSectionListView = (ListView) findViewById(R.id.section_list);
        
        
        
        initFromIntent();
        
        setTitle(mBookTitle);
        
        // Get current book mark.
		mBookMarkSection = getBookMarkSection(mBookUri);
        
		// Get current book content.
        mSections = getBookSections(mBookUri);
        if(mSections != null) {
        	mSectionAdapter = new SectionAdapter(this, mSections);
        	mSectionListView.setAdapter(mSectionAdapter);
            mSectionListView.setOnItemClickListener(this);

            // Scroll to the current book mark section.
            mSectionListView.setSelectionAfterHeaderView();
            mSectionListView.setSelection(mBookMarkSectionPos);
        }
    }
    
	@Override
	protected void onStart() {
		super.onStart();

		if(mSectionAdapter != null) {
			mSectionAdapter.notifyDataSetChanged();
		}
	}

	@Override
	protected void onStop() {
		super.onStop();

		// Save the current reading section to book mark.
		mBookMarkSectionPos = mSectionListView.getFirstVisiblePosition();
		if(mSections != null) {
			Section s = mSections.get(mBookMarkSectionPos);
			saveBookMarkSection(mBookUri, s.name);
		}
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		// Not show comment if comment mode is disabled.
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if(pref != null) {
			boolean enabled = pref.getBoolean(getResources().getString(R.string.key_comment_enable_comment),
					getResources().getBoolean(R.bool.default_value_comment_enable_comment));
			if(!enabled) return;
		}

		// Get section info.
		Section s = mSections.get(position);
		Uri sectionUri = Uri.withAppendedPath(mBookUri, s.name);

		CharSequence sectionText = ((TextView) view).getText();
		if(!s.isMainTitle && !s.isChapterTitle) {
			String chapter = getResources().getString(R.string.section_chapter, s.chapterId, ""); // FIXME: lost chapter title
			sectionText = new StringBuilder().append(chapter).append('\n').append(sectionText);
		}

		// Show current section comment.
		Intent it = new Intent(Intent.ACTION_VIEW, sectionUri, this, CommentActivity.class);
		it.putExtra(CommentActivity.EXTRA_BOOK_TITLE, mBookTitle);
		it.putExtra(CommentActivity.EXTRA_SECTION_CONTENT, sectionText);
		startActivity(it);
	}
	
	private void initFromIntent() {
		Intent it = getIntent();
		if(Intent.ACTION_VIEW.equals(it.getAction())) {
			mBookUri = it.getData();
			mBookTitle = it.getStringExtra(EXTRA_BOOK_TITLE);
		}
	}
	
	private String getBookMarkSection(Uri bookUri) {
		String section = null;

		String bookName = BibleStore.getBookName(bookUri);
		Uri bookMarkUri = Uri.withAppendedPath(BibleStore.BIBLE_MARK_CONTENT_URI, bookName);

		String[] projection = new String[] { BibleStore.BookMarkColumns.SECTION };
		String where = new StringBuilder(
				BibleStore.BookMarkColumns.NAME).append("=\'").append(bookName).append('\'').toString();
		Cursor cursor = getContentResolver().query(bookMarkUri, projection, where, null, null);
		if(cursor != null) {
    		if(cursor.moveToFirst()) {
    			section = cursor.getString(cursor.getColumnIndex(BibleStore.BookMarkColumns.SECTION));
    		}
    		
    		cursor.close();
		}
		
		return section;
	}
	
	private void saveBookMarkSection(Uri bookUri, String section) {
		String bookName = BibleStore.getBookName(bookUri);
		Uri bookMarkUri = Uri.withAppendedPath(BibleStore.BIBLE_MARK_CONTENT_URI, bookName);
		
		ContentValues values = new ContentValues();
		values.put(BibleStore.BookMarkColumns.NAME, bookName);
		values.put(BibleStore.BookMarkColumns.SECTION, section);
		getContentResolver().insert(bookMarkUri, values);
	}
	
	private List<Section> getBookSections(Uri bookUri) {
		if(bookUri == null) return null;

		List<Section> sections = new ArrayList<Section>();

    	String[] projection = new String[] { BibleStore.BookContentColumns.SECTION, BibleStore.BookContentColumns.CONTENT };
    	Cursor cursor = getContentResolver().query(bookUri, projection, null, null, null);
    	if(cursor != null) {
    		if(cursor.moveToFirst()) {
    			int pos = 0;
    			do {
    				// Initialize every section.
    				//
    				Section s = new Section();
    				s.name = cursor.getString(cursor.getColumnIndex(BibleStore.BookContentColumns.SECTION));
    				s.content = cursor.getString(cursor.getColumnIndex(BibleStore.BookContentColumns.CONTENT));
    				if(s.content == null) {
    					s.content = "";
    				}

    				// Get section details according to section name.
    				if(!TextUtils.isEmpty(s.name)) {
    					int index1 = s.name.indexOf('.');
    					try {
    						s.chapterId = s.name.substring(0, index1);
    					} catch(Exception e) {}

    					try {
    						int index2 = s.name.indexOf('t');
    						if(index2 != -1) {
    							s.isTitle = true;
    							s.sectionId = Integer.parseInt(s.name.substring(index1 + 1, index2));
    							s.isMainTitle = (s.sectionId < 0);
    							s.isChapterTitle = (s.sectionId == 0);
    						} else {
    							s.sectionId = Integer.parseInt(s.name.substring(index1 + 1));
    						}
    					} catch(Exception e) {}
    				}
    				
    				// Check whether current section is the book mark section.
    				if(mBookMarkSectionPos <= 0) {
	    				if(!TextUtils.isEmpty(mBookMarkSection)) {
	    					if(mBookMarkSection.equals(s.name)) {
	    						mBookMarkSectionPos = pos;
	    					}
	    				}
    				}

    				sections.add(s);
    				pos ++;
    			} while(cursor.moveToNext());
    		}
    		
    		cursor.close();
    	}
		
		return sections;
	}

	private class SectionAdapter extends BaseAdapter {
		private Context mContext;
		private List<Section> mSections;
		
		public SectionAdapter(Context context, List<Section> sections) {
			mContext = context;
			mSections = sections;
		}

		@Override
		public int getCount() {
			return mSections.size();
		}

		@Override
		public Object getItem(int position) {
			return mSections.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = null;
			
			// Format sections content using different layouts.
			Section s = mSections.get(position);
			CharSequence text = null;
			if(s.isMainTitle) {
				text = s.content;
				view = View.inflate(mContext, R.layout.section_main_title, null);
			} else if(s.isChapterTitle) {
				text = s.content;
				view = View.inflate(mContext, R.layout.section_chapter_title, null);
			} else if(s.isTitle) {
				text = s.content;
				view = View.inflate(mContext, R.layout.section_part_title, null);
			} else { // Main body
				text = getResources().getString(R.string.section_main_body, s.sectionId, s.content);
				view = View.inflate(mContext, R.layout.section_main_body, null);
			}
			
			TextView tv = (TextView) view.findViewById(android.R.id.text1);
			if(tv != null) {
				tv.setText(text);

				// Show indicator for sections with comments.
				Uri sectionUri = Uri.withAppendedPath(mBookUri, s.name);
				String comment = CommentActivity.getComment(mContext, sectionUri);
				if(!TextUtils.isEmpty(comment)) {
					Drawable indicator = mContext.getResources().getDrawable(R.drawable.bookmark4);
					tv.setCompoundDrawablesWithIntrinsicBounds(null, null, indicator, null);
				}
			}

			return view;
		}
	};
}