package net.sunniwell.filedownloadpath.impl;

import java.util.ArrayList;
import java.util.List;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import net.sunniwell.filedownloadpath.db.DownLoadHelper;
import net.sunniwell.filedownloadpath.info.DownloadInfo;

/** 
* 功能: 数据库操作工具类
* @author 作者 郭勇
* 创建时间：2016年8月15日 
*/
public class DownlaodSqlTool {
	
	private static DownlaodSqlTool mSqlTool = null;
	private DownLoadHelper mDbHelper = null;

	private DownlaodSqlTool(Context context) {
		mDbHelper = new DownLoadHelper(context);
	}

	private static synchronized void syncInit(Context context) {
		if (mSqlTool == null) {
			mSqlTool = new DownlaodSqlTool(context);
		}
	}

	public static DownlaodSqlTool getInstance(Context context) {
		if (mSqlTool == null) {
			syncInit(context);
		}
		return mSqlTool;
	}
	
	/** 将下载的进度等信息保存到数据库 */
	public void insertInfos(List<DownloadInfo> infos) {
		SQLiteDatabase database = mDbHelper.getWritableDatabase();
		for (DownloadInfo info : infos) {
			String sql = "insert into download_info(thread_id,start_pos, end_pos,compelete_size,url) values (?,?,?,?,?)";
			Object[] bindArgs = { info.getThreadId(), info.getStartPos(),
					info.getEndPos(), info.getCompeleteSize(), info.getUrl() };
			database.execSQL(sql, bindArgs);
		}
	}

	/** 获取下载的进度等信息 */
	public List<DownloadInfo> getInfos(String urlstr) {
		List<DownloadInfo> list = new ArrayList<DownloadInfo>();
		SQLiteDatabase database = mDbHelper.getWritableDatabase();
		String sql = "select thread_id, start_pos, end_pos,compelete_size,url from download_info where url=?";
		Cursor cursor = database.rawQuery(sql, new String[] { urlstr });
		while (cursor.moveToNext()) {
			DownloadInfo info = new DownloadInfo(cursor.getInt(0),
					cursor.getInt(1), cursor.getInt(2), cursor.getInt(3),
					cursor.getString(4));
			list.add(info);
		}
		cursor.close();
		return list;
	}

	/** 更新数据库中的下载信息 */
	public void updataInfos(int threadId, int compeleteSize, String urlstr) {
		SQLiteDatabase database = mDbHelper.getWritableDatabase();
		String sql = "update download_info set compelete_size=? where thread_id=? and url=?";
		Object[] bindArgs = { compeleteSize, threadId, urlstr };
		database.execSQL(sql, bindArgs);
	}

	/** 关闭数据库 */
	public void closeDb() {
		mDbHelper.close();
	}

	/** 删除数据库中的数据 */
	public void delete(String url) {
		SQLiteDatabase database = mDbHelper.getWritableDatabase();
		database.delete("download_info", "url=?", new String[] { url });
	}
}