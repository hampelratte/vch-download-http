package de.berlios.vch.download.http;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;

import org.osgi.service.log.LogService;

import de.berlios.vch.download.AbstractDownload;
import de.berlios.vch.download.SpeedometerInputStream;
import de.berlios.vch.parser.IVideoPage;

public class HttpDownload extends AbstractDownload {

    private LogService logger;
    
    private File file;
    
    private boolean supportsPause;
    
    private float speed;
    
    public HttpDownload(IVideoPage video, LogService logger) {
        super(video);
        this.logger = logger;
    }

    private int progress = -1;
    
    @Override
    public int getProgress() {
        return progress;
    }

    @Override
    public void run() {
        supportsPause = checkSupportsPause();
        
        HttpURLConnection con = null;
        RandomAccessFile out = null;
        SpeedometerInputStream in = null;
        try {
            file = new File(getLocalFile());
            
            // try to restart download if the file already exists
            long fileLength = 0;
            if(file.exists()) {
                fileLength = file.length();
            }
            out = new RandomAccessFile(file, "rw");
            URI uri = getVideoPage().getVideoUri();
            con = (HttpURLConnection) uri.toURL().openConnection();
            con.addRequestProperty("Range", "bytes=" + fileLength + "-");

            logger.log(LogService.LOG_DEBUG, "Server responded with: " + con.getResponseCode() + " " + con.getResponseMessage());
            if(con.getResponseCode() == 206) { // partial content
                // partial content is supported, we can append to the file
                setLoadedBytes(fileLength);
                out.seek(getLoadedBytes());
            } else {
                // partial content is not supported
                // make a normal request
                setLoadedBytes(0);
                con = (HttpURLConnection) uri.toURL().openConnection();
            }
            
            long contentLength = -1;
            if(con.getHeaderField("Content-Length") != null) {
                // if it is a partial download, we have to add the already 
                // downloaded bytes to get the original size.
                // otherwise getLoadedBytes is 0 and doesn't affect the value
                contentLength = Long.parseLong(con.getHeaderField("Content-Length")) + getLoadedBytes();
            }
            
            // check if the file is complete already 
            if(contentLength == fileLength) {
                logger.log(LogService.LOG_DEBUG, "File is complete already. No need to download anything");
                setStatus(Status.FINISHED);
                progress = 100;
                return;
            }

            
			in = new SpeedometerInputStream(con.getInputStream());
            logger.log(LogService.LOG_DEBUG, "Download started for " + uri.toString() + " at position " + getLoadedBytes());
            setStatus(Status.DOWNLOADING);
            byte[] b = new byte[10240];
            int length = -1;
            while( (length = in.read(b)) > -1) {
                if(Thread.currentThread().isInterrupted() || getStatus() == Status.STOPPED) {
                    setStatus(Status.STOPPED);
                    return;
                }
                
                out.write(b, 0, length);
                increaseLoadedBytes(length);
                
                // calculate the progress
                if(contentLength > -1) {
                    progress = (int) ((double)getLoadedBytes() / (double)contentLength * 100);
                }
                
                // get the speed
                speed = in.getSpeed();
            }
            logger.log(LogService.LOG_DEBUG, "AbstractDownload finished " + uri.toString());
            setStatus(Status.FINISHED);
        } catch (MalformedURLException e) {
            error("Not a valid URL " + getVideoPage().getVideoUri().toString(), e);
            setStatus(Status.FAILED);
            setException(e);
        } catch (IOException e) {
            error("Couldn't download file from " + getVideoPage().getVideoUri().toString(), e);
            setStatus(Status.FAILED);
            setException(e);
        } finally {
        	if(in != null) {
        		try {
					in.close();
				} catch (IOException e) {
					logger.log(LogService.LOG_WARNING, "Couldn't close HTTP stream", e);
				}
        	}

            if(con != null) {
                con.disconnect();
            }
            
            if(out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.log(LogService.LOG_WARNING, "Couldn't close file", e);
                }
            }
        }
    }

    private boolean checkSupportsPause() {
        boolean support = false;
        URI uri = getVideoPage().getVideoUri();
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) uri.toURL().openConnection();
            con.addRequestProperty("Range", "bytes=1-");
            support = con.getResponseCode() == 206;
            logger.log(LogService.LOG_DEBUG, "Pause download supported: " + support);
        } catch (Exception e) {
            logger.log(LogService.LOG_WARNING, "Failed to check support for pausing the download", e);
        } finally {
            if(con != null) {
                con.disconnect();
            }
        }
        
        return support;
    }

    @Override
    public void cancel() {
        // delete the video file
        if(file != null && file.exists()) {
            boolean deleted = file.delete();
            if(!deleted) {
                logger.log(LogService.LOG_WARNING, "Couldn't delete file " + file.getAbsolutePath());
            }
        }
    }

    @Override
    public boolean isPauseSupported() {
        return supportsPause;
    }

    @Override
    public float getSpeed() {
        if(getStatus() == Status.DOWNLOADING) {
            return speed;
        } else {
            return -1;
        }
    }

    @Override
    public void stop() {
        setStatus(Status.STOPPED);
    }

    @Override
    public String getLocalFile() {
        URI uri = getVideoPage().getVideoUri();
        String path = uri.getPath();
        String _file = path.substring(path.lastIndexOf('/') + 1);
        String title = getVideoPage().getTitle().replaceAll("[^a-zA-z0-9]", "_");
        return getDestinationDir() + File.separator + title + "_" + _file;
    }
}