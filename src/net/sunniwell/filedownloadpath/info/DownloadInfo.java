package net.sunniwell.filedownloadpath.info;

/** 
* 功能 :保存每个下载线程下载信息类
* @author 作者 郭勇
* 创建时间：2016年8月15日 
*/
public class DownloadInfo {

	private int threadid; // 下载线程的id
	private int startPos; // 开始点
	private int endPos; // 结束点
	private int completeSize; // 完成度
	private String url; // 下载文件的URL地址

	/**
	 * 
	 * @param threadId
	 *            下载线程的id
	 * @param startPos
	 *            开始点
	 * @param endPos
	 *            结束点
	 * @param compeleteSize
	 *            // 已下载的大小
	 * @param url
	 *            下载地址
	 */
	public DownloadInfo(int threadId, int startPos, int endPos,
			int compeleteSize, String url) {
		this.threadid = threadId;
		this.startPos = startPos;
		this.endPos = endPos;
		this.completeSize = compeleteSize;
		this.url = url;
	}

	public DownloadInfo() {
	}

	/** 获取下载地址 */
	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	/** 获取下载线程的Id */
	public int getThreadId() {
		return threadid;
	}

	public void setThreadId(int threadId) {
		this.threadid = threadId;
	}

	/** 获取下载的开始位置 */
	public int getStartPos() {
		return startPos;
	}

	public void setStartPos(int startPos) {
		this.startPos = startPos;
	}

	/** 获取下载的结束位置 */
	public int getEndPos() {
		return endPos;
	}

	public void setEndPos(int endPos) {
		this.endPos = endPos;
	}

	/** 获取已下载的大小 */
	public int getCompeleteSize() {
		return completeSize;
	}

	public void setCompeleteSize(int compeleteSize) {
		this.completeSize = compeleteSize;
	}

	@Override
	public String toString() {
		return "DownloadInfo [threadId=" + threadid + ", startPos=" + startPos
				+ ", endPos=" + endPos + ", compeleteSize=" + completeSize
				+ "]";
	}
}
