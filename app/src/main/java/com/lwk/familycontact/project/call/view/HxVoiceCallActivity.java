package com.lwk.familycontact.project.call.view;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewStub;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.hyphenate.chat.EMCallStateChangeListener;
import com.hyphenate.exceptions.EMNoActiveCallException;
import com.hyphenate.exceptions.EMServiceNotReadyException;
import com.lib.base.utils.ScreenUtils;
import com.lib.base.utils.StringUtil;
import com.lwk.familycontact.R;
import com.lwk.familycontact.im.helper.HxCallHelper;
import com.lwk.familycontact.im.listener.HxCallStateChangeListener;
import com.lwk.familycontact.project.call.presenter.HxVoiceCallPresenter;
import com.lwk.familycontact.project.chat.utils.HeadSetReceiver;
import com.lwk.familycontact.project.common.CommonUtils;

/**
 * 环信实时语音通话界面
 */
public class HxVoiceCallActivity extends HxBaseCallActivity implements HxVoiceCallView
        , HeadSetReceiver.onHeadSetStateChangeListener
        , SensorEventListener
{
    private static final String INTENT_KEY_PHONE = "opPhone";
    private static final String INTENT_KEY_IS_COMING_CALL = "isComingCall";
    private String mOpPhone;
    private boolean mIsComingCall;
    private HxVoiceCallPresenter mPresenter;
    private ImageView mImgHead;
    private TextView mTvName;
    private TextView mTvDesc;
    private View mViewReceiverPanel;
    private CheckBox mCkMute;
    private CheckBox mCkHandsFree;
    private TextView mTvNetworkUnstable;
    private Chronometer mChronometer;
    private HxCallStateChangeListener mStateChangeListener;
    //是否主动接听电话
    private boolean mHasAnswer = false;
    //主动去电是否被接听
    private boolean mHasAccept = false;
    //挂断后结束界面的延迟时长
    private static final long sDELAY_TIME = 1500L;
    //耳机监听
    private HeadSetReceiver mHeadSetReceiver;
    //距离传感器控制对象
    private SensorManager mSensorManager;
    private Sensor mSensor;
    //亮度控制
    private PowerManager.WakeLock mWakeLock;

    /**
     * 跳转到该界面的公共方法
     *
     * @param context      启动context
     * @param phone        手机号
     * @param isComingCall 是否为接收方
     */
    public static void start(Context context, String phone, boolean isComingCall)
    {
        Intent intent = new Intent(context, HxVoiceCallActivity.class);
        intent.putExtra(INTENT_KEY_PHONE, phone);
        intent.putExtra(INTENT_KEY_IS_COMING_CALL, isComingCall);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void beforeOnCreate(Bundle savedInstanceState)
    {
        ScreenUtils.changStatusbarTransparent(this);
        Intent intent = getIntent();
        mOpPhone = intent.getStringExtra(INTENT_KEY_PHONE);
        mIsComingCall = intent.getBooleanExtra(INTENT_KEY_IS_COMING_CALL, false);
        if (StringUtil.isEmpty(mOpPhone))
            finish();


        //保持屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
    }

    @Override
    protected int setContentViewId()
    {
        mPresenter = new HxVoiceCallPresenter(this, mMainHandler);
        return R.layout.activity_hx_voice_call;
    }

    @Override
    protected void initUI()
    {
        mImgHead = findView(R.id.img_voicecall_avatar);
        mTvName = findView(R.id.tv_voicecall_name);
        mTvDesc = findView(R.id.tv_voicecall_desc);
        mTvNetworkUnstable = findView(R.id.tv_voicecall_network_unstable);
        mChronometer = findView(R.id.chm_voicecall_time);
    }

    @Override
    protected void initData()
    {
        super.initData();
        //添加状态监听
        mStateChangeListener = new HxCallStateChangeListener(mMainHandler, this);
        HxCallHelper.getInstance().addCallStateChangeListener(mStateChangeListener);

        mPresenter.setOpData(mOpPhone);
        mHeadSetReceiver = HeadSetReceiver.registInActivity(this, this);

        //接收到来电时
        if (mIsComingCall)
        {
            showComingCallPanel();
            //播放音乐
            playInComingRingtong(R.raw.incoming_call);
            //震动
            vibrateWithRingtong();
        }
        //主动去电
        else
        {
            showCallingPanel();
            //未接听前静音不可用
            setMuteEnable(false);
            //播放忙音
            playWaittingRingtong(R.raw.outgoing_call);
            try
            {
                HxCallHelper.getInstance().startVoiceCall(mOpPhone);
            } catch (EMServiceNotReadyException e)
            {
                e.printStackTrace();
            }
        }
    }

    //接起电话
    private void pickUpComingCall()
    {
        try
        {
            HxCallHelper.getInstance().answerCall();
            mHasAnswer = true;
            if (mViewReceiverPanel != null)
                mViewReceiverPanel.setVisibility(View.GONE);
            showCallingPanel();
        } catch (EMNoActiveCallException e)
        {
            e.printStackTrace();
        }
    }

    //展示接收到来电panel
    private void showComingCallPanel()
    {
        ViewStub vs = findView(R.id.vs_voicecall_receiver_panel);
        mViewReceiverPanel = vs.inflate();
        addClick(R.id.btn_voicecall_receiver_panel_rejectcall);
        addClick(R.id.btn_voicecall_receiver_panel_answercall);
    }

    //展示通话中/去电等待panel
    private void showCallingPanel()
    {
        ViewStub vs = findView(R.id.vs_voicecall_calling_panel);
        vs.inflate();
        mCkHandsFree = findView(R.id.ck_voicecall_calling_panel_handsfree);
        mCkMute = findView(R.id.ck_voicecall_calling_panel_mute);
        mCkHandsFree.setOnCheckedChangeListener(mHandsFreeListener);
        mCkMute.setOnCheckedChangeListener(mMuteListener);
        addClick(R.id.btn_voicecall_calling_panel_endcall);
    }

    @Override
    public void setHead(String url)
    {
        if (mImgHead != null)
            CommonUtils.getInstance()
                    .getImageDisplayer()
                    .display(this, mImgHead, url, 360, 360, R.drawable.default_avatar, R.drawable.default_avatar);
    }

    @Override
    public void setName(String name)
    {
        if (mTvName != null)
            mTvName.setText(name);
    }

    @Override
    public void connecting()
    {
        if (mTvDesc != null)
            mTvDesc.setText(R.string.call_state_connecting);
    }

    @Override
    public void connected()
    {
        if (mTvDesc != null)
        {
            if (mIsComingCall)
                mTvDesc.setText(R.string.call_state_connected_comingcall);
            else
                mTvDesc.setText(R.string.call_state_connected_outgoingcall);
        }
    }

    @Override
    public void accept()
    {
        if (mTvDesc != null)
            mTvDesc.setText(R.string.call_state_accpet);
        //停止铃声、震动和音乐
        if (mIsComingCall)
        {
            mHasAnswer = true;
            stopInComingRingtong();
            if (mVibratorMgr != null)
                mVibratorMgr.cancel();
        } else
        {
            mHasAccept = true;
            stopWaittingRingtong();
            setMuteEnable(true);
        }
        //震动一下
        vibrateByPickUpPhone();
        //将Mode设为Communication
        if (mAudioMgr != null)
            mAudioMgr.setMode(AudioManager.MODE_IN_COMMUNICATION);
        //监听距离传感器
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
        //开始计时
        mChronometer.setVisibility(View.VISIBLE);
        mChronometer.setBase(SystemClock.elapsedRealtime());
        mChronometer.start();
    }

    @Override
    public void beRejected()
    {
        if (mTvDesc != null)
            mTvDesc.setText(R.string.call_state_be_rejected);
        finishWithDelay();
    }

    @Override
    public void noResponse()
    {
        if (mTvDesc != null)
            mTvDesc.setText(R.string.call_state_no_response);
        finishWithDelay();
    }

    @Override
    public void busy()
    {
        if (mTvDesc != null)
            mTvDesc.setText(R.string.call_state_busy);
        finishWithDelay();
    }

    @Override
    public void offline()
    {
        if (mTvDesc != null)
            mTvDesc.setText(R.string.call_state_offline);
        finishWithDelay();
    }

    @Override
    public void onDisconnect(EMCallStateChangeListener.CallError callError)
    {
        if (mTvDesc != null)
        {
            if (callError == EMCallStateChangeListener.CallError.ERROR_NO_DATA
                    || callError == EMCallStateChangeListener.CallError.ERROR_TRANSPORT
                    || callError == EMCallStateChangeListener.CallError.ERROR_LOCAL_SDK_VERSION_OUTDATED
                    || callError == EMCallStateChangeListener.CallError.ERROR_REMOTE_SDK_VERSION_OUTDATED)
                mTvDesc.setText(R.string.call_state_unknow_error);
            else
                mTvDesc.setText(R.string.call_state_endcall);
        }

        finishWithDelay();
    }

    @Override
    public void onNetworkUnstable(EMCallStateChangeListener.CallError callError)
    {
        if (mTvNetworkUnstable != null)
            mTvNetworkUnstable.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNetworkResumed()
    {
        if (mTvNetworkUnstable != null && mTvNetworkUnstable.getVisibility() == View.VISIBLE)
            mTvNetworkUnstable.setVisibility(View.GONE);
    }

    //设置静音是否可用
    private void setMuteEnable(boolean enable)
    {
        if (mCkMute != null)
            mCkMute.setEnabled(enable);
    }

    //延迟关闭界面
    private void finishWithDelay()
    {
        mMainHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                finish();
            }
        }, sDELAY_TIME);
    }

    @Override
    protected void onClick(int id, View v)
    {
        switch (id)
        {
            case R.id.btn_voicecall_receiver_panel_answercall:
                pickUpComingCall();
                break;
            case R.id.btn_voicecall_receiver_panel_rejectcall:
                try
                {
                    HxCallHelper.getInstance().rejectCall();
                } catch (EMNoActiveCallException e)
                {
                    e.printStackTrace();
                }
                break;
            case R.id.btn_voicecall_calling_panel_endcall:
                try
                {
                    HxCallHelper.getInstance().endCall();
                } catch (EMNoActiveCallException e)
                {
                    e.printStackTrace();
                }
                break;
        }
    }

    //静音开关切换
    private CompoundButton.OnCheckedChangeListener mMuteListener = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            switchMuteMode(isChecked);
        }
    };

    //免提开关切换
    private CompoundButton.OnCheckedChangeListener mHandsFreeListener = new CompoundButton.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            switchHandsFreeMode(isChecked);
        }
    };


    @Override
    public void onHeadSetStateChanged(boolean headSetIn)
    {
        if (headSetIn)
        {
            //插入耳机后免提关闭且设置为不可更改
            if (mCkHandsFree != null)
            {
                mCkHandsFree.setChecked(false);
                mCkHandsFree.setEnabled(false);
            }
            if (mAudioMgr != null)
                mAudioMgr.setMode(AudioManager.MODE_IN_COMMUNICATION);
        } else
        {
            if (mCkHandsFree != null)
                mCkHandsFree.setEnabled(true);
            //耳机拔出后，还未接通通话时设置Mode为Ringtong，否则设置为Communication
            if (mAudioMgr != null)
            {
                if (!mHasAccept && !mHasAnswer)
                    mAudioMgr.setMode(AudioManager.MODE_RINGTONE);
                else
                    mAudioMgr.setMode(AudioManager.MODE_IN_COMMUNICATION);
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event)
    {
        float value = event.values[0];
        if (value == mSensor.getMaximumRange())
            setScreenOn();
        else
            setScreenOff();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy)
    {
        //没用
    }

    //保持屏幕常亮
    private void setScreenOn()
    {
        releaseWakeLock();

        mMainHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                mWakeLock = powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "VoiceCallScreenOn");
                mWakeLock.acquire();
            }
        }, 300);
    }

    //保持屏幕熄灭
    private void setScreenOff()
    {
        releaseWakeLock();

        mMainHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                mWakeLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "VoiceCallScreenOff");
                mWakeLock.acquire();
            }
        }, 300);
    }

    //释放屏幕锁
    private void releaseWakeLock()
    {
        if (mWakeLock != null)
        {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Override
    protected void onDestroy()
    {
        if (mChronometer != null)
            mChronometer.stop();
        HxCallHelper.getInstance().removeCallStateChangeListener(mStateChangeListener);
        HeadSetReceiver.unregistFromActivity(this, mHeadSetReceiver);
        //挂机后亮屏再释放锁
        setScreenOn();
        releaseWakeLock();
        //释放距离传感器
        if (mSensor != null && mSensorManager != null)
        {
            mSensorManager.unregisterListener(this, mSensor);
            mSensor = null;
            mSensorManager = null;
        }
        super.onDestroy();
    }
}
