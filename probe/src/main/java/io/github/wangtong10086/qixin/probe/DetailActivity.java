package io.github.wangtong10086.qixin.probe;
public final class DetailActivity extends android.app.Activity {
 @Override public void onCreate(android.os.Bundle state){super.onCreate(state);android.widget.TextView text=new android.widget.TextView(this);text.setPadding(32,120,32,32);text.setTextSize(22);text.setText("测试目标页面："+getIntent().getStringExtra("page"));setContentView(text);}
}
