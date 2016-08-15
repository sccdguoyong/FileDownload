package net.sunniwell.filedownloadpath;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import net.sunniwell.filedownloadpath.info.DownloadingInfo;
import net.sunniwell.filedownloadpath.service.DownloadUtil;
import net.sunniwell.filedownloadpath.service.DownloadUtil.OnDownloadListener;

public class MainActivity extends FragmentActivity {

	private ListView mListView = null;
	private List<String> mUrls = null;
	private DownloadUtil mDownloadUtil = null;
	private final String TAG_PROGRESS = "_progress";
	private final String TAG_TOTAL = "_total";
	private Button mBtnAppend;
	private EditText mEtUrl;
	private String mStringUrl;
	private Map<String, String> mMap = new HashMap<String, String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mListView = (ListView) findViewById(R.id.listview);
		mEtUrl = (EditText) findViewById(R.id.et_url);
		mBtnAppend = (Button) findViewById(R.id.btn_append);
		mUrls = new ArrayList<String>();
		mBtnAppend.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				mStringUrl = mEtUrl.getText().toString().trim();
				if (mMap != null && mMap.size() > 0) {
					if (!mMap.containsKey(mStringUrl)) {
						mMap.put(mStringUrl, null);
						if (TextUtils.isEmpty(mStringUrl)) {
							Toast.makeText(MainActivity.this, getResources().getString(R.string.enpty_path), Toast.LENGTH_SHORT).show();
						} else {
							mUrls.add(mStringUrl);
							myAdapter.notifyDataSetChanged();
						}
					} else {
						Log.e("gy", "集合中存在该url:" + "--------mStringUrl:-------" + mStringUrl);
					}
				} else {
					mMap.put(mStringUrl, null);
					if (TextUtils.isEmpty(mStringUrl)) {
						Toast.makeText(MainActivity.this, getResources().getString(R.string.enpty_path), Toast.LENGTH_SHORT).show();
					} else {
						mUrls.add(mStringUrl);
						myAdapter.notifyDataSetChanged();
					}
				}
			}
		});

		mListView.setAdapter(myAdapter);

		mDownloadUtil = DownloadUtil.getInstance(this);

		mDownloadUtil.setOnDownloadListener(new OnDownloadListener() {

			String text = "已下载%sM / 共%sM \n占比%s  \n下载速度%skb/s";
			DecimalFormat decimalFormat = new DecimalFormat("#.##"); // 小数格式化
			Timer timer = null;
			Map<String, DownloadingInfo> downloadingInfos = new HashMap<String, DownloadingInfo>();

			@Override
			public void downloadStart(String url, int fileSize) {
				DownloadingInfo info = new DownloadingInfo();
				info.setFileSize(fileSize);
				downloadingInfos.put(url, info);
				((ProgressBar) mListView.findViewWithTag(url + TAG_PROGRESS)).setMax(fileSize);
			}

			@Override
			public synchronized void downloadProgress(String url, int downloadedSize, int length) {
				DownloadingInfo info = downloadingInfos.get(url);
				if (info != null) {
					((ProgressBar) mListView.findViewWithTag(url + TAG_PROGRESS)).setProgress(downloadedSize);
					((TextView) mListView.findViewWithTag(url + TAG_TOTAL)).setText(String.format(text, decimalFormat.format(downloadedSize / 1024.0 / 1024.0), decimalFormat
							.format(info.getFileSize() / 1024.0 / 1024.0), (int) (((float) downloadedSize / (float) info.getFileSize()) * 100) + "%", info.getKbps()));
					info.setSecondSize(info.getSecondSize() + length);
				}
				if (timer == null) {
					timer = new Timer();
					timer.schedule(new TimerTask() {

						@Override
						public void run() {
							DownloadingInfo info = null;
							for (Entry<String, DownloadingInfo> entry : downloadingInfos.entrySet()) {
								info = entry.getValue();
								if (info != null) {
									info.setKbps(decimalFormat.format(info.getSecondSize() / 1024.0));
									info.setSecondSize(0);
								}
							}
						}
					}, 0, 1000);
				}
			}

			@Override
			public void downloadEnd(String url) {
				DownloadingInfo info = downloadingInfos.get(url);
				if (info != null) {
					((ProgressBar) mListView.findViewWithTag(url + TAG_PROGRESS)).setProgress(0);
					((TextView) mListView.findViewWithTag(url + TAG_TOTAL)).setText(String.format(text, decimalFormat.format(0), decimalFormat
							.format(0), 0 + "%", "0"));
					downloadingInfos.remove(url);
				}
			}

		});

	}

	BaseAdapter myAdapter = new BaseAdapter() {

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			Holder holder = null;
			MyOnClickListener listener = null;
			if (convertView == null) {
				convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.list_item, null);
				holder = new Holder();
				listener = new MyOnClickListener(position, holder);
				holder.tv_url = (TextView) convertView.findViewById(R.id.url);
				holder.progressBar = (ProgressBar) convertView.findViewById(R.id.progressBar);
				holder.textView_total = (TextView) convertView.findViewById(R.id.textView_total);
				holder.button_start = (Button) convertView.findViewById(R.id.button_start);
				holder.button_pause = (Button) convertView.findViewById(R.id.button_pause);
				holder.button_resume = (Button) convertView.findViewById(R.id.button_resume);
				holder.button_delete = (Button) convertView.findViewById(R.id.button_delete);

				convertView.setTag(holder);

				setClick(holder,listener);

			} else {
				holder = (Holder) convertView.getTag();
			}

			holder.tv_url.setText(mUrls.get(position));

			holder.progressBar.setTag(mUrls.get(position) + TAG_PROGRESS);
			holder.textView_total.setTag(mUrls.get(position) + TAG_TOTAL);
			holder.button_start.setTag(mUrls.get(position));
			holder.button_pause.setTag(mUrls.get(position));
			holder.button_resume.setTag(mUrls.get(position));
			holder.button_delete.setTag(mUrls.get(position));

			return convertView;
		}

		private void setClick(Holder holder,MyOnClickListener listener) {
			holder.button_start.setOnClickListener(listener);
			holder.button_pause.setOnClickListener(listener);
			holder.button_resume.setOnClickListener(listener);
			holder.button_delete.setOnClickListener(listener);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public Object getItem(int position) {
			return mUrls.get(position);
		}

		@Override
		public int getCount() {
			return mUrls.size();
		}

	};

	class Holder {
		TextView tv_url = null;
		ProgressBar progressBar = null;
		TextView textView_total = null;
		Button button_start = null;
		Button button_pause = null;
		Button button_resume = null;
		Button button_delete = null;
	}

	class MyOnClickListener implements OnClickListener {
		private int position;
		private Holder holder;

		public MyOnClickListener(int position, Holder holder) {
			this.position = position;
			this.holder = holder;
		}

		@Override
		public void onClick(View view) {
			String url = view.getTag() == null ? "" : view.getTag().toString();
			switch (view.getId()) {
			case R.id.button_start:
				mDownloadUtil.prepare(url);
				holder.button_start.setEnabled(false);
				holder.button_pause.setEnabled(true);
				holder.button_resume.setEnabled(false);
				holder.button_delete.setEnabled(true);
				break;
			case R.id.button_pause:
				mDownloadUtil.pause(url);
				holder.button_start.setEnabled(false);
				holder.button_pause.setEnabled(false);
				holder.button_resume.setEnabled(true);
				holder.button_delete.setEnabled(true);
				break;
			case R.id.button_resume:
				mDownloadUtil.resume(url);
				holder.button_start.setEnabled(false);
				holder.button_pause.setEnabled(true);
				holder.button_resume.setEnabled(false);
				holder.button_delete.setEnabled(true);
				break;
			case R.id.button_delete:
				mDownloadUtil.delete(url);
				holder.button_start.setEnabled(true);
				holder.button_pause.setEnabled(false);
				holder.button_resume.setEnabled(false);
				holder.button_delete.setEnabled(true);
				break;

			default:
				break;
			}

		}
	}
}