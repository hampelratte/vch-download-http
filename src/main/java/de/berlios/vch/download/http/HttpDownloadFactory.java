package de.berlios.vch.download.http;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.Invalidate;
import org.apache.felix.ipojo.annotations.Provides;
import org.apache.felix.ipojo.annotations.Requires;
import org.apache.felix.ipojo.annotations.Validate;
import org.osgi.service.log.LogService;

import de.berlios.vch.download.Download;
import de.berlios.vch.download.DownloadFactory;
import de.berlios.vch.download.PlaylistFileFoundException;
import de.berlios.vch.http.client.HttpUtils;
import de.berlios.vch.parser.AsxParser;
import de.berlios.vch.parser.IVideoPage;

@Component
@Provides
public class HttpDownloadFactory implements DownloadFactory {
    
    @Requires
    private LogService logger;
    
    private boolean valid = false;
    
    public HttpDownloadFactory(LogService logger) {
        this.logger = logger;
    }
    
    @Override
    public boolean accept(IVideoPage video) {
        if(valid && video.getVideoUri() != null) {
            return "http".equals(video.getVideoUri().getScheme());
        }
        return false;
    }

    @Override
    public Download createDownload(IVideoPage page) throws IOException, URISyntaxException, PlaylistFileFoundException {
        // some times, we get an playlist file, like asx. we have to cope with that
        Map<String, List<String>> header = HttpUtils.head(page.getVideoUri().toString(), null, "UTF-8");
        if(header.get("Content-Type").size() > 0) {
            String contentType = header.get("Content-Type").get(0);
            if("video/x-ms-asf".equals(contentType)) {
                String videoUri = AsxParser.getUri(page.getVideoUri().toString());
                page.setVideoUri(new URI(videoUri));
                throw new PlaylistFileFoundException();
            }
        }
        
        return new HttpDownload(page, logger);
    }
    
    @Validate
    public void start() {
        valid = true;
    }
    
    @Invalidate
    public void stop() {
        valid = false;
    }
}
