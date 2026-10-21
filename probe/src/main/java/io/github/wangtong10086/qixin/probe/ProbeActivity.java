package io.github.wangtong10086.qixin.probe;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class ProbeActivity extends Activity {
 @Override protected void onNewIntent(android.content.Intent intent){super.onNewIntent(intent);setIntent(intent);render();}
 private void render(){TextView text=new TextView(this);text.setTextSize(22);text.setPadding(32,120,32,32);text.setText("栖信测试\n"+getIntent().getStringExtra("page"));setContentView(text);}
 @Override public void onCreate(Bundle state){super.onCreate(state);render();if(getIntent().getBooleanExtra("notify",false))new ProbeReceiver().onReceive(this,getIntent());}
}
