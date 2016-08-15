package net.sunniwell.filedownloadpath.service;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;
import net.sunniwell.filedownloadpath.impl.DownlaodSqlTool;
import net.sunniwell.filedownloadpath.info.DownloadInfo;

/**
 * 功能: 利用Http协议进行多线程下载具体实现类
 * 
 * @author 作者 郭勇 创建时间：2016年8月15日
 */
public class DownloadHttpTool {

	private final int THREAD_COUNT = 2; // 线程数量
	private String mUrlStr = ""; // URL地址
	private Context mContext = null;
	private List<DownloadInfo> mDownloadInfos = null; // 保存下载信息的类
	/** 下载文件保存路径 */
	public static String mFilePath = ""; // 目录
	private String mFileName = ""; // 文件名
	private String mFileNameTemp = ""; // 临时文件名
	/** 临时文件名后缀 */
	public static final String FILE_TMP_SUFFIX = ".tmp";
	private int mFileSize = 0; // 文件大小
	private DownlaodSqlTool mSqlTool = null; // 文件信息保存的数据库操作类
	private DownloadComplated mDownloadComplated = null;
	private int mTotal = 0;// 所有线程已下载的总数
	private List<DownloadThread> mThreads = null; // 下载线程
	private Handler mHandler = null;

	// 利用枚举表示下载的几种状态
	private enum Download_State {
		Downloading, Pause, Ready, Compeleted, Exception;
	}

	private Download_State state = Download_State.Ready; // 当前下载状态
	private String mFileType;

	public DownloadHttpTool(Context context, Handler handler, DownloadComplated downloadComplated) {
		super();
		this.mContext = context;
		this.mHandler = handler;
		this.mDownloadComplated = downloadComplated;
		mSqlTool = DownlaodSqlTool.getInstance(mContext);
		if ("".equals(mFilePath)) {
			// TODO 根据有无sdcard设置路径
			if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
				mFilePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/guoyong-download";
			}
		}
		mThreads = new ArrayList<DownloadThread>();
	}

	/**
	 * 开始下载
	 * 
	 * @param url
	 *            下载地址
	 */
	public void start(String urlstr) {
		this.mUrlStr = urlstr;
		String[] ss = urlstr.split("/");
		mFileName = ss[ss.length - 1];
		mFileNameTemp = mFileName + FILE_TMP_SUFFIX;

		new AsyncTask<Void, Void, Void>() {

			@Override
			protected Void doInBackground(Void... arg0) {
				// 下载之前首先异步线程调用ready方法做下载的准备工作
				ready();
				Message msg = new Message();
				msg.what = 1;
				msg.arg1 = mFileSize;
				msg.obj = DownloadHttpTool.this.mUrlStr;
				mHandler.sendMessage(msg);
				return null;
			}

			@Override
			protected void onPostExecute(Void result) {
				super.onPostExecute(result);
				// 开始下载
				startDownload();
			}
		}.execute();
	}

	/** 在开始下载之前需要调用ready方法进行配置 */
	private void ready() {
		if (new File(mFilePath + "/" + mFileName).exists()) {
			Log.e("gy", "已下载完成");
			mDownloadComplated.onComplated(mUrlStr);
			return;
		}
		mTotal = 0;
		mDownloadInfos = mSqlTool.getInfos(mUrlStr);
		if (mDownloadInfos.size() == 0) {
			Log.e("gy", " 数据库中没有相关信息");
			initFirst();
		} else {
			File file = new File(mFilePath + "/" + mFileNameTemp);
			if (!file.exists()) {
				mSqlTool.delete(mUrlStr);
				initFirst();
			} else {
				mFileSize = mDownloadInfos.get(mDownloadInfos.size() - 1).getEndPos();
				for (DownloadInfo info : mDownloadInfos) {
					mTotal += info.getCompeleteSize();
				}
			}
		}
	}

	/** 开始下载 */
	private void startDownload() {
		if (mDownloadInfos != null) {
			if (state == Download_State.Downloading) {
				return;
			}
			state = Download_State.Downloading;
			for (DownloadInfo info : mDownloadInfos) { // 开启线程下载
				DownloadThread thread = new DownloadThread(info.getThreadId(), info.getStartPos(), info.getEndPos(), info.getCompeleteSize(), info.getUrl());
				thread.start();
				mThreads.add(thread);
			}
		}
	}

	/** 暂停当前下载任务 */
	public void pause() {
		state = Download_State.Pause;
	}

	/** 删除当前下载任务 */
	public void delete() {
		compeleted();
		File file = new File(mFilePath + "/" + mFileNameTemp);
		file.delete();
	}

	/** 完成下载 */
	private void compeleted() {
		state = Download_State.Compeleted;
		mSqlTool.delete(mUrlStr);
		mDownloadComplated.onComplated(mUrlStr);
	}

	/** 获取目标文件大小 */
	public int getFileSize() {
		return mFileSize;
	}

	/** 获取当前下载的大小 */
	public int getTotalCompeleteSize() {
		return mTotal;
	}

	/** 第一次下载时进行的初始化 */
	private void initFirst() {
		URL url = null;
		RandomAccessFile accessFile = null;
		HttpURLConnection connection = null;
		BufferedInputStream bis = null;
		try {
			url = new URL(mUrlStr);
			connection = (HttpURLConnection) url.openConnection();
			connection.setConnectTimeout(5000);
			connection.setRequestMethod("GET");
			connection.connect();
			mFileSize = connection.getContentLength();
			bis = new BufferedInputStream(connection.getInputStream());
			mFileType = HttpURLConnection.guessContentTypeFromStream(bis);// 得到链接返回的文件类型
			Log.e("gy", mFileType);
			if (mFileSize < 0) {
				return;
			}

			File fileParent = new File(mFilePath);
			if (!fileParent.exists()) {
				fileParent.mkdir();
			}
			File file = new File(fileParent, mFileNameTemp);
			if (!file.exists()) {
				file.createNewFile();
			}
			// 随机访问文件
			accessFile = new RandomAccessFile(file, "rwd");
			accessFile.setLength(mFileSize);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (accessFile != null) {
					accessFile.close();
				}
				if (bis != null) {
					bis.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			if (connection != null) {
				connection.disconnect();
			}
		}
		// 计算每个线程需要下载的大小
		int range = mFileSize / THREAD_COUNT;
		// 保存每个线程的下载信息
		mDownloadInfos = new ArrayList<DownloadInfo>();
		for (int i = 0; i < THREAD_COUNT - 1; i++) {
			DownloadInfo info = new DownloadInfo(i, i * range, (i + 1) * range - 1, 0, mUrlStr);
			mDownloadInfos.add(info);
		}
		// 最后一个线程和前面的处理有点不一样
		DownloadInfo info = new DownloadInfo(THREAD_COUNT - 1, (THREAD_COUNT - 1) * range, mFileSize - 1, 0, mUrlStr);
		mDownloadInfos.add(info);
		// 插入到数据库
		mSqlTool.insertInfos(mDownloadInfos);
	}

	interface DownloadComplated {

		/**
		 * 下载完成回调
		 * 
		 * @param urlString
		 */
		void onComplated(String urlString);

	}

	/** 自定义下载线程 */
	private class DownloadThread extends Thread {

		private int threadId = 0; // 线程Id
		private int startPos = 0; // 在文件中的开始的位置
		private int endPos = 0; // 在文件中的结束的位置
		private int compeleteSize = 0; // 已完成下载的大小
		private String urlstr = ""; // 下载地址

		/**
		 * 
		 * @param threadId
		 *            线程Id
		 * @param startPos
		 *            在文件中的开始的位置
		 * @param endPos
		 *            在文件中的结束的位置
		 * @param compeleteSize
		 *            已完成下载的大小
		 * @param urlstr
		 *            下载地址
		 */
		public DownloadThread(int threadId, int startPos, int endPos, int compeleteSize, String urlstr) {
			this.threadId = threadId;
			this.startPos = startPos;
			this.endPos = endPos;
			this.urlstr = urlstr;
			this.compeleteSize = compeleteSize;
		}

		@Override
		public void run() {
			HttpURLConnection connection = null;
			RandomAccessFile randomAccessFile = null;
			InputStream is = null;
			try {
				randomAccessFile = new RandomAccessFile(mFilePath + "/" + mFileNameTemp, "rwd");
				randomAccessFile.seek(startPos + compeleteSize);
				URL url = new URL(urlstr);
				connection = (HttpURLConnection) url.openConnection();
				connection.setConnectTimeout(5000);
				connection.setRequestMethod("GET");
				// 设置请求的数据的范围
				connection.setRequestProperty("Range", "bytes=" + (startPos + compeleteSize) + "-" + endPos);
				is = connection.getInputStream();
				byte[] buffer = new byte[6 * 1024]; // 6K的缓存
				int length = -1;
				while ((length = is.read(buffer)) != -1) {
					randomAccessFile.write(buffer, 0, length); // 写缓存数据到文件
					compeleteSize += length;
					synchronized (this) { // 加锁保证已下载的正确性
						mTotal += length;
						Message msg = new Message();
						msg.what = 0;
						msg.arg1 = length;
						msg.arg2 = mTotal;
						msg.obj = urlstr;
						mHandler.sendMessage(msg);
					}
					// 非正在下载状态时跳出循环
					if (state != Download_State.Downloading) {
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				Log.e("gy", "异常退出");
				state = Download_State.Exception;
			} finally {
				// 不管发生了什么事，都要保存下载信息到数据库
				mSqlTool.updataInfos(threadId, compeleteSize, urlstr);
				if (mThreads.size() == 1) { // 当前线程是此url对应下载任务唯一一个正在执行的线程
					try {
						if (is != null) {
							is.close();
						}
						if (randomAccessFile != null) {
							randomAccessFile.close();
						}
						if (connection != null) {
							connection.disconnect();
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
					if (state == Download_State.Downloading) { // 此时此线程的下载任务正常完成（没有被人为或异常中断）
						File file = new File(mFilePath + "/" + mFileNameTemp);
						file.renameTo(new File(mFilePath + "/" + mFileName));
					}
					if (state != Download_State.Pause) {
						compeleted();
					}
				}
				mThreads.remove(this);
			}
		}
	}
}