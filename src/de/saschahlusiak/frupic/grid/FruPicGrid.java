package de.saschahlusiak.frupic.grid;

import java.net.UnknownHostException;
import de.saschahlusiak.frupic.R;
import de.saschahlusiak.frupic.about.AboutActivity;
import de.saschahlusiak.frupic.db.FrupicDB;
import de.saschahlusiak.frupic.detail.DetailDialog;
import de.saschahlusiak.frupic.gallery.FruPicGallery;
import de.saschahlusiak.frupic.model.*;
import de.saschahlusiak.frupic.preferences.FrupicPreferences;
import de.saschahlusiak.frupic.utils.UploadActivity;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.DownloadManager.Request;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;

public class FruPicGrid extends Activity implements OnItemClickListener, OnScrollListener {
	static private final String tag = FruPicGrid.class.getSimpleName();
	static private final int REQUEST_PICK_PICTURE = 1;

	GridView grid;
	FruPicGridAdapter adapter;
	FrupicFactory factory;
	ProgressDialog progressDialog;
	Menu optionsMenu;
	View mRefreshIndeterminateProgressView;
	FrupicDB db;
	Cursor cursor;
	
	boolean showFavs = false;

	public final int FRUPICS_STEP = 100;

	RefreshIndexTask refreshTask = null; 
	
	class RefreshIndexTask extends AsyncTask<Void, Void, Frupic[]> {
		String error;
		int base, count;
		
		public RefreshIndexTask(int base, int count) {
			if (base < 0)
				base = 0;
			this.base = base;
			this.count = count;
		}

		@Override
		protected void onPreExecute() {
			Log.d(tag, "RefreshIndexTask.onPreExecute");
			error = null;
			setProgressActionView(true);
			super.onPreExecute();
		}

		@Override
		protected Frupic[] doInBackground(Void... arg0) {
			Frupic pics[];
			try {
				pics = factory.fetchFrupicIndex(null, base, count);
			} catch (UnknownHostException u) {
				pics = null;
				error = "Unknown host";
				cancel(false);
			} catch (Exception e) {
				pics = null;
				error = "Connection error";
				e.printStackTrace();
				cancel(false);
			}
			if (pics == null) {
				/* increasing the frupic index failed, so to prevent the gridview to fire up another
				 * RefreshIndexTask right away, we just add a delay until we can detect network connectivity
				 */
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			return pics;
		}

		@Override
		protected void onCancelled() {
			if (error != null)
				Toast.makeText(FruPicGrid.this, error, Toast.LENGTH_LONG).show();
			setProgressActionView(false);
			super.onCancelled();
			refreshTask = null;
		}

		@Override
		protected void onPostExecute(Frupic result[]) {
			if (result != null && db != null) {
				db.addFrupics(result);
				cursorChanged();
			}
			setProgressActionView(false);

			super.onPostExecute(result);
			refreshTask = null;
		}
	}

	private final class RemoveWindow implements Runnable {
        public void run() {
            removeWindow();
        }
    }

    private RemoveWindow mRemoveWindow = new RemoveWindow();
    Handler mHandler = new Handler();
    private WindowManager mWindowManager;
    private TextView mDialogText;
    private boolean mShowing;
    private boolean mReady;

	
	@Override
	public void onCreate(Bundle savedInstanceState) {		
		super.onCreate(savedInstanceState);
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		}

		setContentView(R.layout.grid_activity);

		factory = new FrupicFactory(this, 300);

		db = new FrupicDB(this);
		db.open();
//		db.clearAll();
		
		adapter = new FruPicGridAdapter(this, factory);
		grid = (GridView) findViewById(R.id.gridView);
		grid.setAdapter(adapter);
		grid.setOnItemClickListener(this);
		grid.setOnScrollListener(this);
		registerForContextMenu(grid);
		
		if (savedInstanceState != null) {
			showFavs = savedInstanceState.getBoolean("showFavs", false);
		}
		
        mWindowManager = (WindowManager)getSystemService(Context.WINDOW_SERVICE);
        
        LayoutInflater inflate = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        
        mDialogText = (TextView) inflate.inflate(R.layout.grid_list_position, null);
        mDialogText.setVisibility(View.INVISIBLE);
        
		mHandler.post(new Runnable() {
			public void run() {
				mReady = true;
				WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
						LayoutParams.WRAP_CONTENT,
						LayoutParams.WRAP_CONTENT,
						WindowManager.LayoutParams.TYPE_APPLICATION,
						WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
								| WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
						PixelFormat.TRANSLUCENT);
				mWindowManager.addView(mDialogText, lp);
			}
		});		
		
		cursorChanged();
	}

	@Override
	protected void onDestroy() {
		/* delete all temporary external cache files created from "Share Image" */
		
		if (cursor != null)
			cursor.close();
		cursor = null;
		db.markAllSeen();
		db.close();
		db = null;
        mWindowManager.removeView(mDialogText);
        mReady = false;		
		super.onDestroy();
	}
	
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("showFavs", showFavs);
	};
	
	@Override
	protected void onStart() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());

		factory.setCacheSize(Integer.parseInt(prefs.getString("cache_size",
				"16777216")));
		factory.updateCacheDirs();
		
		refreshTask = new RefreshIndexTask(0, FRUPICS_STEP); /* TODO: this might skip valuable frupics */
		refreshTask.execute();

		super.onStart();
	}
	
	@Override
	protected void onStop() {
		if (refreshTask != null && !refreshTask.isCancelled())
			refreshTask.cancel(true);
		refreshTask = null;
		
		super.onStop();
	}
	
	@Override
	protected void onResume() {
		mReady = true;
		cursorChanged();
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		removeWindow();
		mReady = false;
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
		Intent intent = new Intent(this, FruPicGallery.class);
		intent.putExtra("position", position);
		intent.putExtra("id", id);
		intent.putExtra("showFavs", showFavs);
		startActivity(intent);
	}

    private void removeWindow() {
        if (mShowing) {
            mShowing = false;
            mDialogText.setVisibility(View.INVISIBLE);
        }
    }
    
    String mPrevDate = "";

    int lastScrollState = SCROLL_STATE_IDLE;
    
	@Override
	public void onScroll(AbsListView view, int firstVisibleItem,
			int visibleItemCount, int totalItemCount) {
		if (!showFavs && firstVisibleItem + visibleItemCount > adapter.getCount() - FRUPICS_STEP) {
			if (refreshTask == null) {
				refreshTask = new RefreshIndexTask(adapter.getCount() - FRUPICS_STEP, FRUPICS_STEP + FRUPICS_STEP);
				refreshTask.execute();
			}
		}
        if (mReady) {
        	Cursor first = (Cursor)adapter.getItem(firstVisibleItem);
        	if (first != null) {
	            String date = first.getString(FrupicDB.DATE_INDEX);
	            if (date == null)
	            	date = "";
	            else date = date.substring(0, 10);
	            
	            if (!mShowing && !date.equals(mPrevDate) && lastScrollState == SCROLL_STATE_FLING) {
	                mShowing = true;
	                mDialogText.setVisibility(View.VISIBLE);
	            }
	            if (mShowing) {
	            	mDialogText.setText(date);
	            	mHandler.removeCallbacks(mRemoveWindow);
	            	mHandler.postDelayed(mRemoveWindow, 1500);
	            }
	            mPrevDate = date;
        	}
        }
	}


	@Override
	public void onScrollStateChanged(AbsListView view, int state) {
		lastScrollState = state;
		if (state == SCROLL_STATE_IDLE && (!showFavs)) {
			int firstVisibleItem = grid.getFirstVisiblePosition();
			int visibleItemCount = grid.getLastVisiblePosition() - firstVisibleItem + 1;
			if (firstVisibleItem + visibleItemCount > adapter.getCount() - FRUPICS_STEP) {

				if (refreshTask != null)
					refreshTask.cancel(true);
				refreshTask = new RefreshIndexTask(adapter.getCount() - FRUPICS_STEP, FRUPICS_STEP + FRUPICS_STEP);
				refreshTask.execute();
			}
			
			Thread t = new Thread() {
				public void run() {					
					factory.pruneCache();
				};
			};
			t.start();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.grid_optionsmenu, menu);
		this.optionsMenu = menu;
		
		/* in case the tasks gets started before the options menu is created */
		if (refreshTask != null)
			setProgressActionView(true);
		
		menu.findItem(R.id.showStars).setIcon(showFavs ? R.drawable.star_label : R.drawable.star_empty);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent intent;

		switch (item.getItemId()) {
		case R.id.refresh:
			refreshTask = new RefreshIndexTask(0, FRUPICS_STEP); /* this might skip valuable frupics */
			refreshTask.execute();
			db.markAllSeen();
			return true;

		case R.id.upload:
			intent = new Intent(Intent.ACTION_PICK);
			intent.setType("image/*");
			startActivityForResult(Intent.createChooser(intent,
					getString(R.string.upload)), REQUEST_PICK_PICTURE);
			return true;
			
		case R.id.showStars:
			showFavs = !showFavs;
			cursorChanged();
			item.setChecked(showFavs);
			item.setIcon(showFavs ? R.drawable.star_label : R.drawable.star_empty);
			return true;

		case R.id.preferences:
			intent = new Intent(this, FrupicPreferences.class);
			startActivity(intent);
			return true;
			
		case R.id.about:
			intent = new Intent(this, AboutActivity.class);
			startActivity(intent);
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(android.view.ContextMenu menu, View v,
			android.view.ContextMenu.ContextMenuInfo menuInfo) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.grid_contextmenu, menu);

		AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
		Frupic frupic = new Frupic((Cursor)adapter.getItem((int) info.position));

		menu.setHeaderTitle("#" + frupic.getId());
		menu.findItem(R.id.star).setChecked(frupic.hasFlag(Frupic.FLAG_FAV));
		if (Build.VERSION.SDK_INT < 9) {
			/* hide Download, which is not supported on API 8 and lower */
			menu.findItem(R.id.cache_now).setVisible(false);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item
				.getMenuInfo();
		Frupic frupic = new Frupic((Cursor)adapter.getItem((int) info.position));
		Intent intent;

		switch (item.getItemId()) {
		case R.id.star:
			frupic.setFlags((frupic.getFlags() ^ Frupic.FLAG_FAV) & ~Frupic.FLAG_NEW);
			db.setFlags(frupic);
			cursorChanged();
			return true;
		case R.id.openinbrowser:
			intent = new Intent("android.intent.action.VIEW", Uri.parse(frupic.getUrl()));
			startActivity(intent);
			return true;

		case R.id.details:
			DetailDialog.create(this, frupic, factory).show();
			
			return true;

		case R.id.share_link:
			intent = new Intent(Intent.ACTION_SEND);
			intent.setType("text/plain");
			intent.putExtra(Intent.EXTRA_TEXT, frupic.getUrl());
			intent.putExtra(Intent.EXTRA_SUBJECT, "FruPic #" + frupic.getId());
			startActivity(Intent.createChooser(intent,
					getString(R.string.share_link)));
			return true;

		case R.id.cache_now:
			/* Make sure, destination directory exists */
			Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).mkdirs();
			
			DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
			DownloadManager.Request req = new DownloadManager.Request(Uri.parse(frupic.getFullUrl()));
			req.setVisibleInDownloadsUi(true);
			
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				req.allowScanningByMediaScanner();
				req.setNotificationVisibility(Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			} else
				req.setShowRunningNotification(true);
			
			req.setTitle(frupic.getFileName(false));
			req.setDescription("Frupic " + frupic.getId());
			req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, frupic.getFileName(false));
			dm.enqueue(req);
			return true;

		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Intent intent;
		super.onActivityResult(requestCode, resultCode, data);

		switch (requestCode) {
		case REQUEST_PICK_PICTURE:
			if (resultCode == RESULT_OK) {
				if (data != null) {
					Uri uri = data.getData();
					intent = new Intent(this, UploadActivity.class);
					intent.setAction(Intent.ACTION_SEND);
					intent.putExtra(Intent.EXTRA_STREAM, uri);

					startActivity(intent);
				}
			}
			break;
		}
	}
	
	void setProgressActionView(boolean refreshing) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			setProgressBarIndeterminateVisibility(refreshing);			
			return;
		}
		if (optionsMenu == null)
			return;
        final MenuItem refreshItem = optionsMenu.findItem(R.id.refresh);
        if (refreshItem != null) {
            if (refreshing) {
                if (mRefreshIndeterminateProgressView == null) {
                    LayoutInflater inflater = (LayoutInflater)
                            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    mRefreshIndeterminateProgressView = inflater.inflate(
                            R.layout.actionbar_indeterminate_progress, null);
                }

                refreshItem.setActionView(mRefreshIndeterminateProgressView);
            } else {
                refreshItem.setActionView(null);
            }
        }
	}
	
	void cursorChanged() {
		if (showFavs)
			cursor = db.getFavFrupics();
		else
			cursor = db.getFrupics(null);
		adapter.changeCursor(cursor);
	}
}