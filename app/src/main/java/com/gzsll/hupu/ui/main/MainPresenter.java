package com.gzsll.hupu.ui.main;

import android.app.Activity;
import android.app.Fragment;
import android.support.annotation.NonNull;
import android.view.MenuItem;
import com.gzsll.hupu.AppManager;
import com.gzsll.hupu.Constants;
import com.gzsll.hupu.R;
import com.gzsll.hupu.components.storage.UserStorage;
import com.gzsll.hupu.db.User;
import com.gzsll.hupu.db.UserDao;
import com.gzsll.hupu.injector.PerActivity;
import com.gzsll.hupu.otto.AccountChangeEvent;
import com.gzsll.hupu.otto.ChangeThemeEvent;
import com.gzsll.hupu.otto.LoginSuccessEvent;
import com.gzsll.hupu.otto.MessageReadEvent;
import com.gzsll.hupu.ui.account.AccountActivity;
import com.gzsll.hupu.ui.browser.BrowserFragment;
import com.gzsll.hupu.ui.forum.ForumListFragment;
import com.gzsll.hupu.ui.messagelist.MessageActivity;
import com.gzsll.hupu.ui.thread.special.SpecialThreadListFragment;
import com.gzsll.hupu.ui.userprofile.UserProfileActivity;
import com.gzsll.hupu.util.SettingPrefUtils;
import com.gzsll.hupu.util.ToastUtils;
import com.gzsll.hupu.util.UpdateAgent;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;
import java.util.List;
import javax.inject.Inject;
import rx.Observable;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

/**
 * Created by sll on 2016/3/17.
 */
@PerActivity public class MainPresenter implements MainContract.Presenter {

  private UserStorage mUserStorage;
  private UserDao mUserDao;
  private Bus mBus;
  private Activity mActivity;
  private Observable<Integer> mNotificationObservable;
  private UpdateAgent mUpdateAgent;

  private Subscription mSubscription;
  private MainContract.View mMainView;
  private int count = 0;

  @Inject public MainPresenter(UserStorage userStorage, UserDao userDao, Bus bus, Activity activity,
      Observable<Integer> notificationObservable, UpdateAgent mUpdateAgent) {
    mUserStorage = userStorage;
    mUserDao = userDao;
    mBus = bus;
    mActivity = activity;
    mNotificationObservable = notificationObservable;
    this.mUpdateAgent = mUpdateAgent;
  }

  @Override public void attachView(@NonNull MainContract.View view) {
    mMainView = view;
    mBus.register(this);
    initUserInfo();
    initNotification();
    if (SettingPrefUtils.getAutoUpdate(mActivity)) {
      mUpdateAgent.checkUpdate(mActivity);
    }
  }

  private void initUserInfo() {
    mMainView.renderUserInfo(isLogin() ? mUserStorage.getUser() : null);
  }

  private void initNotification() {
    if (isLogin()) {
      mSubscription = mNotificationObservable.subscribe(new Action1<Integer>() {
        @Override public void call(Integer integer) {
          if (integer == null) {
            ToastUtils.showToast("登录信息失效，请重新登录");
            mUserDao.queryBuilder()
                .where(UserDao.Properties.Uid.eq(mUserStorage.getUid()))
                .buildDelete()
                .executeDeleteWithoutDetachingEntities();
            mUserStorage.logout();
            mMainView.showLoginUi();
          } else {
            count = integer;
            mMainView.renderNotification(integer);
          }
        }
      }, new Action1<Throwable>() {
        @Override public void call(Throwable throwable) {

        }
      });
    }
  }

  private void toLogin() {
    mMainView.showLoginUi();
    ToastUtils.showToast("请先登录");
  }

  @Override public void onNightModelClick() {
    SettingPrefUtils.setNightModel(mActivity, !SettingPrefUtils.getNightModel(mActivity));
    mMainView.reload();
  }

  @Override public void onNotificationClick() {
    if (isLogin()) {
      MessageActivity.startActivity(mActivity);
    } else {
      toLogin();
    }
  }

  @Override public void onCoverClick() {
    if (isLogin()) {
      UserProfileActivity.startActivity(mActivity, mUserStorage.getUid());
    } else {
      toLogin();
    }
    mMainView.closeDrawers();
  }

  @Override public void onNavigationClick(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.nav_recommend:
      case R.id.nav_collect:
      case R.id.nav_topic:
      case R.id.nav_nba:
      case R.id.nav_my:
      case R.id.nav_cba:
      case R.id.nav_gambia:
      case R.id.nav_equipment:
      case R.id.nav_fitness:
      case R.id.nav_football:
      case R.id.nav_intel_football:
      case R.id.nav_sport:
        Fragment mFragment = null;
        int id = item.getItemId();
        if (id == R.id.nav_collect) {
          if (isLogin()) {
            mFragment =
                SpecialThreadListFragment.newInstance(SpecialThreadListFragment.TYPE_COLLECT);
          } else {
            toLogin();
          }
        } else if (id == R.id.nav_topic) {
          if (isLogin()) {
            mFragment = BrowserFragment.newInstance(mUserStorage.getUser().getThreadUrl(), "我的帖子");
          } else {
            toLogin();
          }
        } else if (id == R.id.nav_recommend) {
          mFragment =
              SpecialThreadListFragment.newInstance(SpecialThreadListFragment.TYPE_RECOMMEND);
        } else {
          if (isLogin() || id != R.id.nav_my) {
            mFragment = ForumListFragment.newInstance(Constants.mNavMap.get(id));
          } else {
            toLogin();
          }
        }
        if (mFragment != null) {
          item.setChecked(true);
          mMainView.setTitle(item.getTitle());
          mMainView.showFragment(mFragment);
        }
        break;
      case R.id.nav_setting:
        mMainView.showSettingUi();
        break;
      case R.id.nav_feedback:
        mMainView.showFeedBackUi();

        break;
      case R.id.nav_about:
        mMainView.showAboutUi();
        break;
    }
    mMainView.closeDrawers();
  }

  @Override public void showAccountMenu() {
    Observable.create(new Observable.OnSubscribe<List<User>>() {
      @Override public void call(Subscriber<? super List<User>> subscriber) {
        final List<User> userList = mUserDao.queryBuilder().list();
        for (User bean : userList) {
          if (bean.getUid().equals(mUserStorage.getUid())) {
            userList.remove(bean);
            break;
          }
        }
        subscriber.onNext(userList);
      }
    })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Action1<List<User>>() {
          @Override public void call(List<User> users) {
            final String[] items = new String[users.size() + 1];
            for (int i = 0; i < users.size(); i++)
              items[i] = users.get(i).getUserName();
            items[items.length - 1] = "账号管理";
            mMainView.renderAccountList(users, items);
          }
        });
  }

  @Override
  public void onAccountItemClick(int position, final List<User> users, final String[] items) {
    if (position == items.length - 1) {
      // 账号管理
      AccountActivity.startActivity(mActivity);
    } else {
      mUserStorage.login(users.get(position));
      initUserInfo();
    }
    mMainView.closeDrawers();
  }

  @Override public void exist() {
    if (isCanExit()) {
      AppManager.getAppManager().AppExit(mActivity);
    }
  }

  private long mExitTime = 0;

  private boolean isCanExit() {
    if (System.currentTimeMillis() - mExitTime > 2000) {
      ToastUtils.showToast("再按一次退出程序");
      mExitTime = System.currentTimeMillis();
      return false;
    }
    return true;
  }

  @Override public boolean isLogin() {
    return mUserStorage.isLogin();
  }

  @Override public void detachView() {
    mBus.unregister(this);
    count = 0;
    if (mSubscription != null && !mSubscription.isUnsubscribed()) {
      mSubscription.unsubscribe();
    }
    mMainView = null;
  }

  @Subscribe public void onChangeThemeEvent(ChangeThemeEvent event) {
    mMainView.reload();
  }

  @Subscribe public void onLoginSuccessEvent(LoginSuccessEvent event) {
    initUserInfo();
  }

  @Subscribe public void onAccountChangeEvent(AccountChangeEvent event) {
    initUserInfo();
  }

  @Subscribe public void onMessageReadEvent(MessageReadEvent event) {
    if (count >= 1) {
      count--;
    }
    mMainView.renderNotification(count);
  }
}
