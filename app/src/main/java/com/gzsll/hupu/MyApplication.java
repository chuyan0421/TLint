package com.gzsll.hupu;

import android.app.Application;
import android.os.Environment;
import com.facebook.cache.disk.DiskCacheConfig;
import com.facebook.common.internal.Supplier;
import com.facebook.common.util.ByteConstants;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.backends.okhttp3.OkHttpImagePipelineConfigFactory;
import com.facebook.imagepipeline.cache.MemoryCacheParams;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.decoder.SimpleProgressiveJpegConfig;
import com.gzsll.hupu.components.storage.UserStorage;
import com.gzsll.hupu.db.User;
import com.gzsll.hupu.db.UserDao;
import com.gzsll.hupu.injector.component.ApplicationComponent;
import com.gzsll.hupu.injector.component.DaggerApplicationComponent;
import com.gzsll.hupu.injector.module.ApplicationModule;
import com.gzsll.hupu.util.SettingPrefUtils;
import com.gzsll.hupu.util.ToastUtils;
import com.liulishuo.filedownloader.FileDownloader;
import com.liulishuo.filedownloader.util.FileDownloadHelper;
import de.mindpipe.android.logging.log4j.LogConfigurator;
import java.util.List;
import javax.inject.Inject;
import okhttp3.OkHttpClient;
import org.apache.log4j.Level;

/**
 * Created by sll on 2016/3/8.
 */
public class MyApplication extends Application {

  private ApplicationComponent mApplicationComponent;

  @Inject UserStorage mUserStorage;
  @Inject UserDao mUserDao;
  @Inject OkHttpClient mOkHttpClient;

  @Override public void onCreate() {
    super.onCreate();
    initComponent();
    initLogger();
    initUser();
    FileDownloader.init(this, new FileDownloadHelper.OkHttpClientCustomMaker() {
      @Override public OkHttpClient customMake() {
        return mOkHttpClient;
      }
    });
    initFrescoConfig();
    ToastUtils.register(this);
  }

  private void initComponent() {
    mApplicationComponent =
        DaggerApplicationComponent.builder().applicationModule(new ApplicationModule(this)).build();
    mApplicationComponent.inject(this);
  }

  public ApplicationComponent getApplicationComponent() {
    return mApplicationComponent;
  }

  private void initLogger() {
    String path = getLogPath();
    try {
      final LogConfigurator lc = new LogConfigurator();
      lc.setFileName(path);
      lc.setFilePattern("%d - [%-6p-%c] - %m%n");
      lc.setMaxBackupSize(2);
      lc.setMaxFileSize(1024 * 1024);
      lc.setRootLevel(Level.DEBUG);
      // Set log level of a specific logger
      lc.setLevel("org.apache", Level.DEBUG);
      lc.configure();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public String getLogPath() {
    return Environment.getExternalStorageDirectory().getAbsolutePath() + "/gzsll/log.txt";
  }

  private void initUser() {
    List<User> users = mUserDao.queryBuilder()
        .where(UserDao.Properties.Uid.eq(SettingPrefUtils.getLoginUid(this)))
        .list();
    if (!users.isEmpty()) {
      mUserStorage.login(users.get(0));
    }
  }

  private static final int MAX_HEAP_SIZE = (int) Runtime.getRuntime().maxMemory();

  public static final int MAX_DISK_CACHE_SIZE = 50 * ByteConstants.MB;
  public static final int MAX_MEMORY_CACHE_SIZE = MAX_HEAP_SIZE / 8;

  private void initFrescoConfig() {
    final MemoryCacheParams bitmapCacheParams =
        new MemoryCacheParams(MAX_MEMORY_CACHE_SIZE, // Max total size of elements in the cache
            Integer.MAX_VALUE,                     // Max entries in the cache
            MAX_MEMORY_CACHE_SIZE, // Max total size of elements in eviction queue
            Integer.MAX_VALUE,                     // Max length of eviction queue
            Integer.MAX_VALUE);
    ImagePipelineConfig config = OkHttpImagePipelineConfigFactory.newBuilder(this, mOkHttpClient)
        .setProgressiveJpegConfig(new SimpleProgressiveJpegConfig())
        .setBitmapMemoryCacheParamsSupplier(new Supplier<MemoryCacheParams>() {
          public MemoryCacheParams get() {
            return bitmapCacheParams;
          }
        })
        .setMainDiskCacheConfig(
            DiskCacheConfig.newBuilder(this).setMaxCacheSize(MAX_DISK_CACHE_SIZE).build())
        .setDownsampleEnabled(true)
        .build();
    Fresco.initialize(this, config);
  }
}
