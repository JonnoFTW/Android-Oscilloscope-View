package com.example.osctest;

import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
	MonitorDataSource mMonitorDataSource;
	private FakePlot topPlot;
	private FakePlot botPlot;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);
		LinearLayout top = (LinearLayout) findViewById(R.id.ll1);
		LinearLayout bot = (LinearLayout) findViewById(R.id.ll2);
		OscView topOsc = new OscView(this);
		OscView botOsc = new OscView(this);
		int[] samplePulse = { 61, 61, 62, 63, 62, 61, 61, 61, 61, 67, 71, 68,
				61, 53, 61, 61, 61, 61, 61, 61, 61, 61, 62, 63, 64, 64, 64, 63,
				62, 61, 61, 61 };
		int[] samplePleth = { 30, 31, 31, 34, 39, 41, 46, 50, 53, 57, 62, 67,
				74, 77, 82, 86, 87, 88, 88, 87, 86, 81, 79, 72, 69, 66, 62, 59,
				58, 57, 57, 58, 58, 57, 56, 54, 52, 48, 47, 45, 44, 42, 38, 36,
				34, 33, 32, 32, 30 };
		topPlot = new FakePlot(samplePulse, 60, 800, Color.YELLOW, 50,"Top");
		botPlot = new FakePlot(samplePleth, 34, 185, Color.WHITE, 6,"Bottom");
		topOsc.setPlot(topPlot);
		botOsc.setPlot(botPlot);
		top.addView(topOsc);
		bot.addView(botOsc);
		final DisplayMetrics dp = getResources().getDisplayMetrics();
		for (final OscView oscView : Arrays.asList(topOsc,botOsc)) {
			oscView.getViewTreeObserver().addOnPreDrawListener(new OnPreDrawListener() {
				@Override
				public boolean onPreDraw() {
					oscView.getViewTreeObserver().removeOnPreDrawListener(
							this);
					float pixPerMm = dp.xdpi / 25.4f;
					((FakePlot)oscView.getPlot()).setRate(oscView.getWidth(),getResources().getDisplayMetrics());
					return true;
				}
			});
		}
		
	}
	@Override
	public void onPause() {
		super.onPause();
		mMonitorDataSource.quit();
	}
	@Override
	public void onResume() {
		super.onResume();
		mMonitorDataSource = new MonitorDataSource(Arrays.asList(topPlot,botPlot));
		mMonitorDataSource.start();
	}
	static class FakePlot extends OscView.Plot {
		private static final String TAG = FakePlot.class.getSimpleName();

		// mdata is all the points that are displayed
		
		private int[] mPulse, mData;
		private String mWaveName = "none";
		private long lastPulse,loopStart = SystemClock.elapsedRealtime();
		private int i=0,pulsePosition=0,mMin,mMax,mColor,mScanRateMM,mEraser;
		long pulseDelay,lastUpdate =SystemClock.elapsedRealtime();
		private boolean pulsing = true;
		private String mTitle;
		private float pxPerSec,pxPerTick,mWidthMm;
		
		public FakePlot(int[] pulse, int bpm, int size, int color,int scanRate, String string) {
			if(size <= 0) {
				throw new IllegalArgumentException("Size must be > 0");
			}
			setTitle(string);
			setPulse(pulse);
			setColor(color);
			setPulsePerMinute(bpm);
			mScanRateMM = scanRate;
			mData = new int[size];
			mEraser = size/30;
			Arrays.fill(mData, mPulse[0]);
			setChanged();
		}

		public void setLoopStart(long now) {
			loopStart = now; 
		}
		public void setWaveName(String value) {
			mWaveName = value;
		}

		public void setPulse(int[] data) {
			mPulse = data;
			mMin = data[0];
			mMin = data[0];
			for (int i = 0; i < data.length; i++) {
				mMin = Math.min(mMin, data[i]);
				mMax = Math.max(mMax, data[i]);
			}
			mMin -= 5;
			mMax +=5;
			pulsePosition = 0;
		}
		public void setPulsePerMinute(int bpm) {
			pulseDelay = DateUtils.MINUTE_IN_MILLIS /bpm;
		}
		public void setRate(int width, DisplayMetrics dm) {
			float xdpi = dm.xdpi;
			mWidthMm = width/ xdpi * 25.4f;
			pxPerSec =TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mScanRateMM, dm);
			pxPerTick = ((float)width) / mData.length;
			lastPulse = SystemClock.elapsedRealtime();
			Log.e(TAG,String.format("setRate() %s: pxPerSec=%f pxPerTick=%f",mTitle,pxPerSec,pxPerTick));
		}

		/**
		 * Copy the next set of points along
		 * @param realTime
		 */
		public void updateData(long realTime) {
			long diff = realTime - lastUpdate;
			lastUpdate = realTime;
			float pxToDraw = ((diff/1000f)/pxPerSec);
			float ticksToDraw = pxToDraw/(1f/pxPerTick);
			for (int j = 0; j < ticksToDraw; j++){
				int at = i % mData.length;
				// we need a loop in here so that it draws the appropriate
				// amount of points to match the desired scroll rate
				if (pulsing) {
					synchronized (mPulse) {
						mData[at] = mPulse[pulsePosition++];
					}
					if(pulsePosition >= mPulse.length) {
						pulsing = false;
						pulsePosition = 0;
					}
					
				} else {
					mData[at] = mPulse[0];
					if (realTime - lastPulse > pulseDelay) {
						pulsing = true;
						lastPulse = realTime;
					}
				}
				mData[(at+mEraser) % mData.length] = 0;
				i++;
				
				if(i >= mData.length) {
					float loopdiff =( realTime - loopStart )/ 1000f;
					loopStart = realTime;;
					Log.i(TAG,"Looped in "+loopdiff+"s "+(mWidthMm/loopdiff)+" mm/s");
				}
				i%= mData.length;
						
			}
			setChanged();
			notifyObservers();
		}

		@Override
		public int getColor() {
			return mColor;
		}

		@Override
		public int getMinValue() {
			return mMin;
		}

		@Override
		public int getMaxValue() {
			return mMax;
		}
		@Override
		public String getTitle() {
			return mTitle;
		}
		@Override
		public void setTitle(String title) {
			mTitle = title;
		}
		
		@Override
		public int getValue(int index) {
			if(index < 0)
				return mData[mData.length-1];
			return mData[index];
		}

		@Override
		public int getCount() {
			return mData.length;
		}
		@Override
		public int getFlat() {
			return mPulse[0];
		}
		public String getWaveName() {
			return mWaveName;
		}
		@Override
		public void setColor(int c) {
			mColor = c;
		}
	}
	private static class MonitorDataSource extends Thread {
		private boolean mQuit = false;
		private static final String TAG = MonitorDataSource.class.getSimpleName();
		private List<FakePlot> mPlots;
		public void quit() {
			mQuit = true;
		}
		MonitorDataSource(List<FakePlot> plots) {
			mPlots = plots;
			Log.i(TAG,"New data source made");
		}
		@Override
		public void run() {
			try {
				Log.i(TAG, "Running pulse thread");
				while(!mQuit) {
					for (FakePlot i : mPlots) {
						i.updateData(SystemClock.elapsedRealtime());
					}
					Thread.sleep(17);
				}
			} catch (InterruptedException e) {}
			Log.i(TAG,"quit()");
		}
	}
	public static class OscView extends View {
		private static final String TAG = OscView.class.getSimpleName();
		private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private final Path mPath = new Path();
		private Plot mPlot = null;
		private boolean mDirty = false;
		private String mTitle = "";

		public OscView(Context context) {
			super(context);
			initView(context, null);
		}
		public Plot getPlot() {
			return mPlot;
		}
		public OscView(Context context, AttributeSet attrs) {
			super(context, attrs);
			initView(context, attrs);
		}

		public OscView(Context context, AttributeSet attrs, int defStyle) {
			super(context, attrs, defStyle);
			initView(context, attrs);
		}

		private void initView(Context context, AttributeSet attrs) {
			mPaint.setStyle(Paint.Style.FILL_AND_STROKE);
			mPaint.setStrokeWidth(2.0f);

		}

		public void setPlot(Plot plot) {
			if (mPlot != null) {
				mPlot.deleteObserver(mPlotObserver);
			}
			mPlot = plot;
			if (plot != null) {
				plot.addObserver(mPlotObserver);
				plot.notifyObservers();
			}
		}

		public Paint getPaint() {
			return mPaint;
		}

		@Override
		protected void onDraw(Canvas canvas) {
			if (mPlot != null) {
				mPaint.setStyle(Paint.Style.STROKE);
				mPaint.setStrokeWidth(2f);
				mPaint.setColor(mPlot.getColor());
				canvas.drawPath(mPath, mPaint);
				mPaint.setStrokeWidth(1f);
				mPaint.setColor(Color.BLACK);
				mPaint.setStyle(Paint.Style.FILL);
				canvas.drawRect(9, getHeight() / 2 - 20,
						mPaint.measureText(mPlot.getTitle()) + 11,
						getHeight() / 2 + 5, mPaint);
				mPaint.setColor(mPlot.getColor());
				mPaint.setTextSize(20f);
				canvas.drawText(mPlot.getTitle(), 10, getHeight() / 2, mPaint);
			}
		}

		private void updatePlotPath() {
			final Plot plot = mPlot;
			final float w = getWidth();
			final float h = getHeight();

			final float count = plot.getCount();
			final float range = (plot.getMaxValue() - plot.getMinValue());
			final float xStep = w / count;
			final float yStep = (h / range);

			float x = 0, y = 0;
			mPath.rewind();
			for (int i = 0; i < count; i++) {

				int preVal = plot.getValue(i - 1);
				int val = plot.getValue(i);
				if (val == 0) {
					y = (yStep) * (plot.getMaxValue() - plot.getFlat());
					mPath.moveTo(x, y);
				} else {
					y = (yStep * (plot.getMaxValue() - val));
					if (i == 0) {
						mPath.moveTo(x, y);
					} else {
						if (preVal == 0)
							mPath.moveTo(x, y);
						else
							mPath.lineTo(x, y);
					}
				}
				x += xStep;
			}
		}

		private Observer mPlotObserver = new Observer() {
			@Override
			public void update(final Observable observable, Object data) {
				post(new Runnable() {
					@Override
					public void run() {
						if (!mDirty && observable == mPlot) {
							mDirty = true;
							getViewTreeObserver().addOnPreDrawListener(
									mOnPreDraw);
							invalidate();
						}
					}
				});
			}
		};

		private ViewTreeObserver.OnPreDrawListener mOnPreDraw = new ViewTreeObserver.OnPreDrawListener() {
			@Override
			public boolean onPreDraw() {
				getViewTreeObserver().removeOnPreDrawListener(this);
				updatePlotPath();
				mDirty = false;
				return true;
			}
		};

		public static abstract class Plot extends Observable {
			public abstract int getColor();
			public abstract void setColor(int c);
			public abstract int getFlat();
			public abstract int getMinValue();
			public abstract int getMaxValue();
			public abstract int getValue(int index);
			public abstract int getCount();
			public abstract String getTitle();
			public abstract void setTitle(String title);
		}
	}
}
