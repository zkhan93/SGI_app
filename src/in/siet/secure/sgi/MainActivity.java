package in.siet.secure.sgi;

import in.siet.secure.Util.Utility;
import in.siet.secure.adapters.DrawerListAdapter;
import in.siet.secure.contants.Constants;
import in.siet.secure.dao.DbHelper;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;


public class MainActivity extends ActionBarActivity{
	public static final String TAG="in.siet.secure.sgi.MainActivity";
	private String[] panelOption;
	private DrawerLayout drawerlayout;
	private ListView drawerListView;
	private boolean back_pressed=false;
	private static ActionBarDrawerToggle drawerToggle;
	//private int active_drawer_option;
	static final UserFilterDialog show=new UserFilterDialog();
	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		
		if(savedInstanceState==null){
			setContentView(R.layout.activity_main);
			getFragmentManager().beginTransaction()
			.setTransitionStyle(R.anim.abc_fade_out)
			.add(R.id.mainFrame,new FragmentNotification(),FragmentNotification.TAG).commit();
		//	active_drawer_option=0;
		//	getSupportActionBar().setLogo(getResources().getDrawable(R.drawable.ic_launcher__lite_white));
		//set drawer
		
		if(getApplicationContext()
				.getSharedPreferences(getString(R.string.preference_file_name), Context.MODE_PRIVATE)
				.getBoolean(getString(R.string.is_faculty), false))
		{
			panelOption=getResources().getStringArray(R.array.array_panel_options_fact);
		}
		else{
			panelOption=getResources().getStringArray(R.array.array_panel_options);
		}
		drawerlayout=(DrawerLayout)findViewById(R.id.drawer_layout);
		drawerListView=(ListView)findViewById(R.id.drawer_listview);
		drawerListView.setAdapter(new DrawerListAdapter(this,panelOption));
		drawerListView.setOnItemClickListener(new DrawerClickListner());
		drawerToggle=new ActionBarDrawerToggle(this, drawerlayout,R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close);
		drawerlayout.setDrawerListener(drawerToggle);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);
		
		DisplayImageOptions options=new DisplayImageOptions.Builder()
		.cacheInMemory(true)
		.cacheOnDisk(true)
		.build();
		
		ImageLoaderConfiguration config=new ImageLoaderConfiguration.Builder(getApplicationContext())
		.defaultDisplayImageOptions(options)
		.build();
		ImageLoader.getInstance().init(config);
		
		}
		
	}
	@Override
	public void onResume(){
		super.onResume();
		back_pressed=false;
	}
	 @Override
	    protected void onPostCreate(Bundle savedInstanceState) {
	        super.onPostCreate(savedInstanceState);
	        // Sync the toggle state after onRestoreInstanceState has occurred.
	        drawerToggle.syncState();
	 }
	 
	 @Override
	    public void onConfigurationChanged(Configuration newConfig) {
	        super.onConfigurationChanged(newConfig);
	        drawerToggle.onConfigurationChanged(newConfig);
	    }
	 
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		//super.onCreateOptionsMenu(menu);
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		
		//handle drawer open/close clicks
		back_pressed=false;
		if (drawerToggle.onOptionsItemSelected(item)) {
	          return true;
		}
		
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			//startActivity(SettingsActivity.class);
			getFragmentManager().beginTransaction().replace(R.id.mainFrame, new FragmentSettings(),FragmentSettings.TAG)
			.commit();
			return true;
		}
		else if(id == R.id.action_logout) {
			getApplicationContext().getSharedPreferences(getString(R.string.preference_file_name), Context.MODE_PRIVATE).edit().clear().commit();
			Log.d(TAG,"pref cleared");
			startActivity(LoginActivity.class);
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	@Override
	public void onBackPressed(){
		if(!back_pressed){
			back_pressed=true;
			Utility.RaiseToast(getApplicationContext(), getString(R.string.exit_warning), true);
		}
		else{
			super.onBackPressed();
		}
	}
	public void startActivity(Class<?> activity){
		Intent intent=new Intent(this,activity);
		Log.d(TAG,"stating login Activity");
		startActivity(intent);
	}
	
	public void switch_fragment(int position){
		FragmentManager fragmentManager=getFragmentManager();
		FragmentTransaction fragmentTransaction=fragmentManager.beginTransaction().setTransitionStyle(R.anim.abc_fade_out);
		Fragment fragment;//=fragmentManager.findFragmentByTag(TAG+panelOption[position]);
		switch(position){
		case Constants.DrawerIDs.NOTIFICATION:
			fragment=fragmentManager.findFragmentByTag(FragmentNotification.TAG);
			if(fragment==null)
				fragment=new FragmentNotification();
			fragmentTransaction.replace(R.id.mainFrame,fragment,FragmentNotification.TAG)
			.commit();
			break;
		case Constants.DrawerIDs.INTERACTION:
			fragment=fragmentManager.findFragmentByTag(FragmentContacts.TAG);
			if(fragment==null)
				fragment=new FragmentContacts();
			fragmentTransaction.replace(R.id.mainFrame,fragment,FragmentContacts.TAG)
			.commit();
			break;
		case Constants.DrawerIDs.ADD_USER:
			fragment=getFragmentManager().findFragmentByTag(TAG+"FragmentUsers");
			if(fragment==null)
				fragment=new FragmentUsers();
			fragmentTransaction.replace(R.id.mainFrame, fragment, FragmentUsers.TAG)
			.commit();	
			break;
		case Constants.DrawerIDs.SETTING:
			fragment=fragmentManager.findFragmentByTag(FragmentSettings.TAG);
			if(fragment==null)
				fragment=new FragmentSettings();
			fragmentTransaction.replace(R.id.mainFrame,fragment,FragmentSettings.TAG)
			.commit();
			break;
		case Constants.DrawerIDs.CREATE_NOTICE: //only for faculty
			fragment=fragmentManager.findFragmentByTag(FragmentNewNotification.TAG);
			if(fragment==null)
				fragment=new FragmentNewNotification();
			fragmentTransaction.replace(R.id.mainFrame,fragment,FragmentNewNotification.TAG)
			.commit();
			break;
		default:
			Toast.makeText(getApplicationContext(),getString(R.string.wrong_choice), Toast.LENGTH_SHORT).show();
			return;
		}
	}
	
	public class DrawerClickListner implements OnItemClickListener {

		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			drawerListView.setItemChecked(position, true);
			Bundle bundle=new Bundle();
			if(position==Constants.DrawerIDs.ADD_USER){
				bundle.putInt(UserFilterDialog.FRAGMENT_TO_OPEN,Constants.DrawerIDs.ADD_USER);
				show.setArguments(bundle);
				show.show(getFragmentManager(), UserFilterDialog.TAG);
			}
			else if(position==Constants.DrawerIDs.CREATE_NOTICE){
				bundle.putInt(UserFilterDialog.FRAGMENT_TO_OPEN,Constants.DrawerIDs.CREATE_NOTICE);
				show.setArguments(bundle);
				show.show(getFragmentManager(), UserFilterDialog.TAG);
			}
			else
				switch_fragment(position);
			
			drawerlayout.closeDrawer(drawerListView);
			
		}
	}
	
	public void sendNewNotification(View view){
		
		((TextView)(view.getRootView().findViewById(R.id.editTextNewNoticeSubject))).setText("");
		((TextView)(view.getRootView().findViewById(R.id.editTextNewNoticeBody))).setText("");
		new DbHelper(getApplicationContext()).addNewNotification((FragmentNewNotification.ViewHolder)(view.getRootView().getTag()));
		Utility.RaiseToast(getApplicationContext(), "send new message", false);
	}
}
