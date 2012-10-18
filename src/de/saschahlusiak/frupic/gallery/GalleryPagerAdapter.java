package de.saschahlusiak.frupic.gallery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;

import de.saschahlusiak.frupic.R;
import de.saschahlusiak.frupic.model.Frupic;
import de.saschahlusiak.frupic.model.FrupicFactory;
import de.saschahlusiak.frupic.model.FrupicFactory.OnFetchProgress;

import android.database.Cursor;
import android.graphics.Bitmap;
import android.support.v4.view.PagerAdapter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class GalleryPagerAdapter extends PagerAdapter {
	private final static String tag = GalleryPagerAdapter.class.getSimpleName();

	FrupicFactory factory;
	FruPicGallery context;
	Cursor cursor;
	boolean showAnimations;
	
	class FetchTask extends Thread implements OnFetchProgress {
		Frupic frupic;
		View view;
		boolean cancelled = false;
		ProgressBar progress;
		TextView progressText;

		FetchTask(final View view, final Frupic frupic) {
			this.view = view;
			this.frupic = frupic;
			final ImageButton stopButton = (ImageButton)view.findViewById(R.id.stopButton);
			
			progress = (ProgressBar)view.findViewById(R.id.progressBar);
			progress.setIndeterminate(false);
			progress.setMax(100);
			progress.setProgress(0);
			progress.setVisibility(View.VISIBLE);
			
			progressText = (TextView)view.findViewById(R.id.progress);
			progressText.setVisibility(View.VISIBLE);
			progressText.setText(R.string.waiting_to_start);
			
			stopButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
			stopButton.setVisibility(View.VISIBLE);
			stopButton.setOnClickListener(new OnClickListener() {
				
				@Override
				public void onClick(View v) {
					if (isCancelled()) {
						Thread t = new FetchTask(view, frupic);
						view.setTag(t);
						t.start();
					} else {
						stopButton.setImageResource(android.R.drawable.ic_menu_revert);
						progressText.setText(R.string.cancelled);
						cancel();
					}
				}
			});
		}
	
		public synchronized void cancel() {
			if (cancelled)
				return;
			cancelled = true;
			interrupt();
		}
	
		public synchronized boolean isCancelled() {
			return cancelled;
		}

		@Override
		public void run() {
			if (isCancelled()) {
				return;
			}

			final int ret;

			ret = factory.fetchFull(frupic, this);
			if (isCancelled())
				return;

			view.post(new Runnable() {
				@Override
				public void run() {
					ImageView i = (ImageView)view.findViewById(R.id.imageView);
					GifMovieView v = (GifMovieView)view.findViewById(R.id.videoView);
					
					Bitmap b = factory.getFullBitmap(frupic);

					if (b != null) {
						showFrupic(view, frupic, b);
					} else {
						i.setVisibility(View.VISIBLE);
						v.setVisibility(View.GONE);
						i.setImageResource(R.drawable.broken_frupic);
					}
					progress.setVisibility(View.GONE);
					progressText.setVisibility(View.GONE);
					view.findViewById(R.id.stopButton).setVisibility(View.GONE);
				}
			});
		}

		@Override
		public void OnProgress(final int read, final int length) {
			view.post(new Runnable() {
				@Override
				public void run() {
					if (isCancelled())
						return;
					progress.setProgress((100 * read) / length);
					progressText.setText(String.format("%dkb / %dkb (%d%%)", read / 1024, length / 1024, (length > 0) ? read * 100 / length : 0));
				}
			});
		}
	}
	
	public GalleryPagerAdapter(FruPicGallery context, FrupicFactory factory, boolean showAnimations) {
		this.context = context;
		this.factory = factory;
		this.showAnimations = showAnimations;
	}

	public void setCursor(Cursor cursor) {
		this.cursor = cursor;
	}
	
	public void showFrupic(View view, Frupic frupic, Bitmap b) {
		GifMovieView v = (GifMovieView)view.findViewById(R.id.videoView);
		ImageView i = (ImageView)view.findViewById(R.id.imageView);
		if (showAnimations && frupic.isAnimated()) {
			v.setVisibility(View.VISIBLE);
			i.setVisibility(View.GONE);
			String filename = factory.getCacheFileName(frupic, false);
            InputStream stream = null;
			try {
				stream = new FileInputStream(filename);
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				byte buf[] = new byte[4096];
				while (stream.read(buf) > 0) {
					bos.write(buf);
				}
				bos.flush();
				stream.close();
				stream = new ByteArrayInputStream(bos.toByteArray());
			} catch (Exception e1) {
				e1.printStackTrace();
			}
			
			v.setStream(stream);
		} else {
			i.setVisibility(View.VISIBLE);
			v.setVisibility(View.GONE);
			i.setImageBitmap(b);
		}

	}

	@Override
	public Object instantiateItem(ViewGroup container, int position) {
		Log.w(tag, "instantiateItem(" + position + ")");
		cursor.moveToPosition(position);

		Frupic frupic = new Frupic(cursor);

		View view = LayoutInflater.from(context).inflate(R.layout.gallery_item, container, false);
		ImageView i = (ImageView)view.findViewById(R.id.imageView);
		GifMovieView v = (GifMovieView)view.findViewById(R.id.videoView);
		
		context.registerForContextMenu(i);
		context.registerForContextMenu(v);
		i.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				context.showControls();
			}
		});
		v.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				context.showControls();
			}
		});

		
		Bitmap b = factory.getFullBitmap(frupic);

		if (b == null) {
			i.setVisibility(View.VISIBLE);
			v.setVisibility(View.GONE);
			
			i.setImageResource(R.drawable.frupic);
			Thread t = new FetchTask(view, frupic);
			view.setTag(t);
			t.start();
		} else {
			view.findViewById(R.id.progressBar).setVisibility(View.GONE);
			view.findViewById(R.id.stopButton).setVisibility(View.GONE);
			view.findViewById(R.id.progress).setVisibility(View.GONE);

			showFrupic(view, frupic, b);
		}
		
		container.addView(view);
		return view;
	}
	
	@Override
	public void destroyItem(ViewGroup container, int position, Object object) {
		View view = (View)object;
		FetchTask t = (FetchTask)view.getTag();
		if (t != null)
			t.cancel();
		container.removeView(view);
	}

	@Override
	public int getCount() {
		return cursor.getCount();
	}

	@Override
	public boolean isViewFromObject(View view, Object object) {
		return view == object;
	}
}
