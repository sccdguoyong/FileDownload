package net.sunniwell.filedownloadpath.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import net.sunniwell.filedownloadpath.service.DownloadHttpTool.DownloadComplated;
/** 
* 功能 : 将下载方法封装在此类 提供开始、暂停、删除以及重置的方法。
 * 通过修改常量{@link DownloadUtil#MAX_COUNT}可改变最大并行下载任务量。
* @author 作者 郭勇
* 创建时间：2016年8月15日 
*/
public class DownloadUtil {

	private static DownloadUtil mUtil = null;
	private Context mContext = null;
	private List<String> mDownloadLists = null;
	private Map<String, DownloadHttpTool> mDownloadMap = null;
	private int mCurrentIndex = -1; //当前位置
	private final int MAX_COUNT = 2; // 最大并行下载量
	private int mCurrentCount = 0; // 当前并行下载量
	private final String FLAG_FREE = "free"; // 标记downloadMap中空闲的DownloadHttpTool实例
	private OnDownloadListener mDownloadListener = null;

	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			String url = msg.obj.toString();
			if (msg.what == 0) {
				if (mDownloadListener != null) {
					mDownloadListener
							.downloadProgress(url, msg.arg2, msg.arg1);
				}
			} else if (msg.what == 1) {
				if (mDownloadListener != null) {
					mDownloadListener.downloadStart(url, msg.arg1);
				}
			} else if (msg.what == 2) {
				mDownloadListener.downloadEnd(url);
			}
		}

	};

	private DownloadUtil(Context context) {
		this.mContext = context;
		mDownloadLists = new ArrayList<String>();
		mDownloadMap = new HashMap<String, DownloadHttpTool>();
	}

	private static synchronized void syncInit(Context context) {
		if (mUtil == null) {
			mUtil = new DownloadUtil(context);
		}
	}

	public static DownloadUtil getInstance(Context context) {
		if (mUtil == null) {
			syncInit(context);
		}
		return mUtil;
	}

	/**
	 * 下载之前的准备工作，并自动开始下载
	 * 
	 * @param context
	 */
	public void prepare(String urlString) {
		mDownloadLists.add(urlString);
		if (mCurrentCount < MAX_COUNT) {
			start();
		} else {
			Log.e("gy", "等待下载");
		}
	}

	/**
	 * 开始下载
	 */
	private synchronized void start() {
		if (++mCurrentIndex >= mDownloadLists.size()) {
			mCurrentIndex--;
			return;
		}
		mCurrentCount++;
		String urlString = mDownloadLists.get(mCurrentIndex);
		Log.e("gy", "开始下载");
		DownloadHttpTool downloadHttpTool = null;
		if (mDownloadMap.size() < MAX_COUNT) { 
			downloadHttpTool = new DownloadHttpTool(mContext, mHandler,
					downloadComplated);
			if (mDownloadMap.containsKey(urlString)) {
				mDownloadMap.remove(urlString);
			}
			mDownloadMap.put(urlString, downloadHttpTool);
		} else {
			downloadHttpTool = mDownloadMap.get(FLAG_FREE);
			mDownloadMap.remove(FLAG_FREE);
			mDownloadMap.put(urlString, downloadHttpTool);
		}
		downloadHttpTool.start(urlString);
	}

	/** 暂停当前下载任务 */
	public void pause(String urlString) {
		paused(urlString, new Paused() {

			@Override
			public void onPaused(DownloadHttpTool downloadHttpTool) {
				downloadHttpTool.pause();
			}
		});
	}

	/** 暂停所有的下载任务 */
	public void pauseAll() {
		// 如果需要边遍历集合边删除数据，需要从后向前遍历，否则会出异常（Caused by:
		// java.util.ConcurrentModificationException）
		String[] keys = new String[mDownloadMap.size()];
		mDownloadMap.keySet().toArray(keys);
		for (int i = keys.length - 1; i >= 0; i--) {
			pause(keys[i]);
		}
		mUtil = null;
	}

	/**
	 * 恢复当前下载任务
	 * 
	 * @param urlString
	 *            要恢复下载的文件的地址
	 */
	public void resume(String urlString) {
		prepare(urlString);
	}

	/** 恢复所有的下载任务 */
	public void resumeAll() {
		for (Entry<String, DownloadHttpTool> entity : mDownloadMap.entrySet()) {
			prepare(entity.getKey());
		}
	}

	/** 删除当前下载任务 */
	public void delete(String urlString) {
		boolean bool = paused(urlString, new Paused() {

			@Override
			public void onPaused(DownloadHttpTool downloadHttpTool) {
				downloadHttpTool.pause();
				downloadHttpTool.delete();
			}
		});
		if (!bool) { // 下载任务不存在，直接删除临时文件
			File file = new File(DownloadHttpTool.mFilePath + "/"
					+ urlString.split("/")[urlString.split("/").length - 1]
					+ DownloadHttpTool.FILE_TMP_SUFFIX);
			file.delete();
		}
	}

	interface Paused {

		void onPaused(DownloadHttpTool downloadHttpTool);

	}

	/**
	 * 暂停
	 * 
	 * @param urlString
	 * @param paused
	 * @return 下载任务是否存在的标识
	 */
	private boolean paused(String urlString, Paused paused) {
		if (mDownloadMap.containsKey(urlString)) {
			mCurrentCount--;
			DownloadHttpTool downloadHttpTool = mDownloadMap.get(urlString);
			paused.onPaused(downloadHttpTool);
			if (!mDownloadMap.containsKey(FLAG_FREE)) { // 保证key == FLAG_FREE的数量
														// = 1
				mDownloadMap.put(FLAG_FREE, downloadHttpTool);
			}
			mDownloadMap.remove(urlString);
			start();
			return true;
		}
		return false;
	}

	DownloadComplated downloadComplated = new DownloadComplated() {

		@Override
		public void onComplated(String urlString) {
			Log.e("gy", "下载完成");
			Message msg = new Message();
			msg.what = 2;
			msg.obj = urlString;
			mHandler.sendMessage(msg);
			pause(urlString);
			// 满足此条件说明全部下载结束
			if (mDownloadMap.size() == 1 && mDownloadMap.containsKey(FLAG_FREE)) {
				Log.e("gy", "全部下载结束");
				String filePath = "";
				if ("".equals(filePath)) {
					// TODO 根据有无sdcard设置路径
					if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
						filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/guoyong-download";
					}
				}
				String[] ss = urlString.split("/");
				String fileName = ss[ss.length - 1];
				File file = new File(filePath + "/" + fileName +".tmp");
				file.renameTo(new File(filePath + "/" + fileName+".apk"));
			}
		}
	};

	/** 设置下载监听 */
	public void setOnDownloadListener(OnDownloadListener onDownloadListener) {
		this.mDownloadListener = onDownloadListener;
	}

	/** 下载回调接口 */
	public interface OnDownloadListener {

		/**
		 * 下载开始回调接口
		 * 
		 * @param url
		 * @param fileSize
		 *            目标文件大小
		 */
		public void downloadStart(String url, int fileSize);

		/**
		 * 下载进度回调接口
		 * 
		 * @param
		 * @param downloadedSize
		 *            已下载大小
		 * @param lenth
		 *            本次下载大小
		 */
		public void downloadProgress(String url, int downloadedSize, int length);

		/**
		 * 下载完成回调
		 * 
		 * @param url
		 */
		public void downloadEnd(String url);

	}

}