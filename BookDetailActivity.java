package com.mobioapp.paidpdf.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.util.IabHelper;
import com.android.vending.billing.util.Security;
import com.blundell.tutorial.simpleinappbillingv3.domain.items.Passport;
import com.blundell.tutorial.simpleinappbillingv3.ui.base.BlundellActivity;
import com.blundell.tutorial.simpleinappbillingv3.ui.utils.Navigator;
import com.boipoka.database.BBHDatabaseManager;
import com.boipoka.database.Book;
import com.boipoka.database.BookBundle;
import com.boipoka.help.BKashHelpScreen;
import com.boipoka.help.ViewPagerActivity;
import com.boipoka.qrreader.QRScannerActivity;
import com.boipoka.util.ConnectionDetector;
import com.boipoka.util.ConstantMessageUrls;
import com.boipoka.util.ImageLoaderHelper;
import com.boipoka.util.JSONPoster;
import com.boipoka.util.SharedData;
import com.mkh.customdialogs.CustomProgressDialog;
import com.mobioapp.paidpdf.R;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.assist.ImageLoadingListener;
import com.nostra13.universalimageloader.core.display.SimpleBitmapDisplayer;

public class BookDetailActivity extends BlundellActivity implements
		OnClickListener {

	ImageLoader imageLoader;
	ImageLoadingListener animateFirstListener = new ImageLoaderHelper.AnimateFirstDisplayListener();
	DisplayImageOptions displayOptions;
	ImageView imgBook;
	// ImageView imgBookPage1, imgBookPage2, imgBookPage3;
	Button btnCashOnDelivery, btnGooglePlay, btnDownload;
	TextView tvBookName, tvBookAuthor, tvBookPrice, tvBookPublisher,
			tvBookCategory, tvTotalPages, tvSamplePages;
	RatingBar rbRating;
	boolean isBookAlreadyPurchased = false;
	private Book book;
	private SharedData sharedData;
	private BBHDatabaseManager database;
	private Map<Long, Integer> downloadTracker = new HashMap<Long, Integer>();
	private DownloadManager downloadManager;
	private ConnectionDetector connecctionDetector;
	private static String transactionId, activationCode, bankName,
			accountNumber, pokaId = " ";
	public static EditText setActivationCode;
	int checkedButtonID = 1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.book_detail_new);

		sharedData = SharedData.getInstance();
		book = sharedData.getBookFromBookFragment();

		Passport.SKU = book.in_app_purchase_id;

		database = BBHDatabaseManager.getInstance(BookDetailActivity.this);
		connecctionDetector = new ConnectionDetector(BookDetailActivity.this);

		Bundle extras = getIntent().getExtras();
		isBookAlreadyPurchased = extras.getBoolean("isBookAlreadyPurchased");

		initUI();
	}

	private void onPurchaseItemClick() {

		Log.e("On Purchase", "On Purchase");
		navigate().toPurchasePassportActivityForResult();

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d("", "onActivityResult(" + requestCode + "," + resultCode + ","
				+ data);

		if (requestCode == 1) {

			if (resultCode == RESULT_OK) {
				String result = data.getStringExtra("result");
				// System.out.println("OnActivityResult2: " + result);
				setActivationCode.setText(result);
			}
			if (resultCode == RESULT_CANCELED) {
				// Write your code if there's no result
			}
		}

		if (requestCode == Navigator.REQUEST_PASSPORT_PURCHASE) {
			SharedPreferences prefs = getSharedPreferences("IAB", 0);
			String resultText = prefs.getString("success", "");
			if (resultCode == RESULT_OK || !TextUtils.isEmpty(resultText)) {

				dealWithSuccessfulPurchase(Passport.SKU);

			} else if (resultCode == IabHelper.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
				popToast("Item already owned.");
			} else {
				dealWithFailedPurchase();
			}
		}

	}

	private CreateNewProduct mTask;

	private void dealWithSuccessfulPurchase(String sku) {
		Log.e("", "Passport purchased");
		// popToast("Item purchased: " + sku);
		System.out.println("Item Purchased  " + sku);
		if (!TextUtils.isEmpty(sku)) {
			if (mTask != null) {
				mTask.cancel(true);
			}
			mTask = new CreateNewProduct(generateDeviceId(), getUserEmail(),
					sku, getCurrentTime());
			mTask.execute();
		}
		SharedPreferences.Editor editor = getSharedPreferences("IAB", 0).edit();
		editor.putString("success", "");
		editor.commit();
	}

	@Override
	protected void onDestroy() {
		if (mTask != null) {
			mTask.cancel(true);
		}
		if (mTask2 != null) {
			mTask2.cancel(true);
		}
		super.onDestroy();
	}

	private void dealWithFailedPurchase() {
		Log.d("", "Passport purchase failed");
		popToast("Item purchase failed.");
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnDetailBuyFromGooglePlay:

			// if (isRooted()) {
			// Toast.makeText(this, "Unable to purchase item!!",
			// Toast.LENGTH_LONG).show();
			// } else {
			boolean installed = appInstalledOrNot("cc.madkite.freedom");
			if (installed) {
				popToast("Unable to buy book!!");
			} else {
				onPurchaseItemClick();
			}

			// }

			break;
		case R.id.btnDetailBuyCashOnDelivery:
			if (connecctionDetector.isConnectingToInternet()) {
				CashDeliveryOptions(book);
			} else {
				connecctionDetector.connectionAlert();
			}
			break;
		case R.id.btnDetailDownload:
			downloadManager(book, BookDetailActivity.this);
			finish();
			break;
		case R.id.tvSampleLink:
			sharedData.setBook(book);
			Intent i = new Intent(BookDetailActivity.this,
					ViewPagerActivity.class);
			i.putExtra("isFromBookDetailActivity", true);
			i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(i);
			break;
		}
	}

	private class CreateNewProduct extends AsyncTask<String, String, String> {

		/**
		 * Before starting background thread Show Progress Dialog
		 * */

		JSONPoster jsonParser = new JSONPoster();

		private CustomProgressDialog pDialog;
		final Book book = sharedData.getBook();

		private static final String TAG_SUCCESS = "success";
		// PurchaseInfo purchase = new PurchaseInfo();

		String purchaseEmail, purchaseTime, purchaseDeviceId,
				purchaseProductId;

		public CreateNewProduct(String purchaseDeviceId, String purchaseEmail,
				String purchaseProductId, String purchaseTime) {
			this.purchaseDeviceId = purchaseDeviceId;
			this.purchaseEmail = purchaseEmail;
			this.purchaseProductId = purchaseProductId;
			this.purchaseTime = purchaseTime;
			// isPurchaseSucceeded = false;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			Log.e("Create New Product", "AsyncTask started: onPreExecute()");
			pDialog = new CustomProgressDialog(BookDetailActivity.this);
			try {
				pDialog.setTitle(R.string.app_name);
				pDialog.setMessage("Please wait...");
				pDialog.setIndeterminate(false);
				pDialog.setCancelable(true);
				pDialog.show();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		/**
		 * Creating product
		 * */
		protected String doInBackground(String... args) {

			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("pemail", purchaseEmail));
			params.add(new BasicNameValuePair("dID", purchaseDeviceId));
			params.add(new BasicNameValuePair("spID", purchaseProductId));
			params.add(new BasicNameValuePair("pTime", purchaseTime));

			Log.e("", purchaseEmail + " " + purchaseDeviceId + " "
					+ purchaseProductId + " " + purchaseTime);

			JSONObject json = jsonParser.makeHttpRequest(
					ConstantMessageUrls.BUY_FROM_GOOGLE_PLAY, "POST", params);

			try {
				int success = json.getInt(TAG_SUCCESS);

				if (success == 1) {

				} else {

				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			return null;
		}

		/**
		 * After completing background task Dismiss the progress dialog
		 * **/
		protected void onPostExecute(String file_url) {
			/* dismiss the dialog once done */
			try {
				if (pDialog != null)
					pDialog.dismiss();
			} catch (Exception e) {
				e.printStackTrace();
			}
			database.insertDeviceStorageBooks(generateDeviceId(), book.book_id,
					1);
			downloadManager(book, BookDetailActivity.this);
			finish();
		}
	}

	/** Downloads books from the server */
	@SuppressWarnings("unchecked")
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	@SuppressLint({ "NewApi", "WorldReadableFiles" })
	public void downloadManager(Book book, Context context) {
		File direct = new File(Environment.getExternalStorageDirectory()
				+ "/.BanglaBookHouse/PdfResource");
		if (!direct.exists()) {
			direct.mkdirs();
		}
		String url = "http://mobioapp.net/bardhaman_house/pdf/"
				+ book.file_name;

		downloadTracker.clear();
		@SuppressWarnings("deprecation")
		SharedPreferences prefs = context.getSharedPreferences(
				"DownloadTracker", Context.MODE_WORLD_READABLE);
		Map<String, Integer> maps = new HashMap<String, Integer>();
		maps = (HashMap<String, Integer>) prefs.getAll();
		for (String s : maps.keySet()) {
			downloadTracker.put(Long.valueOf(s), maps.get(s));
		}

		Iterator<?> queueMap = downloadTracker.entrySet().iterator();
		boolean notAddedToMap = true;

		/* summing total cost */
		while (queueMap.hasNext()) {
			@SuppressWarnings("rawtypes")
			Map.Entry pairs = (Map.Entry) queueMap.next();
			int id = (Integer) pairs.getValue();

			if (id == book.book_id) {
				notAddedToMap = false;
				Toast.makeText(context, "Already added to download queue!",
						Toast.LENGTH_LONG).show();
				break;
			}
		}

		if (notAddedToMap) {
			DownloadManager.Request request = new DownloadManager.Request(
					Uri.parse(url));
			request.setDescription("by " + book.writer_name);
			request.setTitle("" + book.title);

			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.GINGERBREAD_MR1) {
				request.allowScanningByMediaScanner();
				request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			}
			request.setDestinationInExternalPublicDir(
					"/.BanglaBookHouse/PdfResource", book.file_name);
			/* get download service and enqueue file */
			downloadManager = (DownloadManager) context
					.getSystemService(Context.DOWNLOAD_SERVICE);

			long enqueue = downloadManager.enqueue(request);

			// System.out.println("STARTER: donwload id: " + enqueue
			// + " book.title: " + book.title);
			downloadTracker.put(enqueue, book.book_id);

			@SuppressWarnings("deprecation")
			SharedPreferences pref = context.getSharedPreferences(
					"DownloadTracker", Context.MODE_WORLD_READABLE);
			SharedPreferences.Editor editor = pref.edit();

			for (Long s : downloadTracker.keySet()) {
				editor.putInt("" + s, downloadTracker.get(s));
				// System.out.println(s + ": " + sdownloadTracker.get(s));
			}
			editor.commit();
		}

	}

	/**
	 * generates device id
	 * */
	public String generateDeviceId() {
		String sdID = "35" + Build.BOARD.length() % 10 + Build.BRAND.length()
				% 10 + Build.CPU_ABI.length() % 10 + Build.DEVICE.length() % 10
				+ Build.DISPLAY.length() % 10 + Build.HOST.length() % 10
				+ Build.ID.length() % 10 + Build.MANUFACTURER.length() % 10
				+ Build.MODEL.length() % 10 + Build.PRODUCT.length() % 10
				+ Build.TAGS.length() % 10 + Build.TYPE.length() % 10
				+ Build.USER.length() % 10;
		return sdID;
	}

	/**
	 * returns Current Time Stamp
	 * */
	public String getCurrentTime() {
		java.util.Date date = new java.util.Date();
		java.sql.Timestamp ts = new Timestamp(date.getTime());
		return String.valueOf(ts.getTime());
	}

	/**
	 * gets default user email from device
	 * */
	public String getUserEmail() {
		String email = "";
		Account[] accounts = null;
		try {
			AccountManager accountManager = AccountManager
					.get(BookDetailActivity.this);
			accounts = accountManager.getAccountsByType("com.google");
		} catch (Exception e) {
			e.printStackTrace();
		}

		Account account;
		if (accounts.length > 0) {
			account = accounts[0];
		} else {
			account = null;
		}

		try {
			email = account.name;
		} catch (Exception e) {

			e.printStackTrace();
			email = "No default email found!";
		}
		return email;
	}

	private void CashDeliveryOptions(final Book book) {

		final Dialog dialog = new Dialog(BookDetailActivity.this,
				R.style.DialogTheme);
		dialog.setContentView(R.layout.dialog_cash_payment_new);
		final Button btnSubmit = (Button) dialog.findViewById(R.id.btnSubmit);
		final RadioGroup radioGroup = (RadioGroup) dialog
				.findViewById(R.id.radiogroup);
		final RadioButton radioActivationCode = (RadioButton) dialog
				.findViewById(R.id.radiobuttonActivationCode);
		final RadioButton radioBKashTransaction = (RadioButton) dialog
				.findViewById(R.id.radiobuttonBkashTransaction);
		dialog.findViewById(R.id.radiobuttonBankTransaction);

		setActivationCode = (EditText) dialog
				.findViewById(R.id.etActivationCode);
		final EditText etTransactionID = (EditText) dialog
				.findViewById(R.id.etBkashTransactionID);
		final TextView etUserEmail = (TextView) dialog
				.findViewById(R.id.etEmailAddress);
		final EditText etBankName = (EditText) dialog
				.findViewById(R.id.etBankName);
		final EditText etBankAccountNumber = (EditText) dialog
				.findViewById(R.id.etBankAccountNumber);
		final TextView tvBookName = (TextView) dialog
				.findViewById(R.id.tvBookName);
		final ImageView qRCodeReader = (ImageView) dialog
				.findViewById(R.id.idQRScanner);

		final ImageView bKashHelpLink = (ImageView) dialog
				.findViewById(R.id.idBKashHelp);

		bKashHelpLink.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				Intent i = new Intent(BookDetailActivity.this,
						BKashHelpScreen.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(i);
			}
		});

		qRCodeReader.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				Intent i = new Intent(BookDetailActivity.this,
						QRScannerActivity.class);
				startActivityForResult(i, 1);
				setActivationCode.setText(sharedData.getmQrResult());

			}
		});
		etUserEmail.setText(getUserEmail().toString());

		tvBookName.setText(book.title);

		radioActivationCode.setChecked(true);
		radioBKashTransaction.setChecked(false);
		//
		setActivationCode.setEnabled(true);
		etTransactionID.setEnabled(false);
		etBankName.setEnabled(false);
		etBankAccountNumber.setEnabled(false);

		radioGroup
				.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(RadioGroup group, int checkedId) {

						switch (checkedId) {
						case R.id.radiobuttonActivationCode:
							checkedButtonID = 1;
							setActivationCode.setEnabled(true);
							setActivationCode.setFocusableInTouchMode(true);
							setActivationCode.requestFocus();
							etTransactionID.setEnabled(false);
							etBankName.setEnabled(false);
							etBankAccountNumber.setEnabled(false);

							etTransactionID.setText("");
							etBankName.setText("");
							etBankAccountNumber.setText("");

							etTransactionID.setHint("Transaction id");
							etBankName.setHint("enter bank name");
							etBankAccountNumber.setHint("enter account number");

							break;
						case R.id.radiobuttonBkashTransaction:
							checkedButtonID = 2;
							setActivationCode.setEnabled(false);
							etTransactionID.setEnabled(true);
							etTransactionID.setFocusableInTouchMode(true);
							etTransactionID.requestFocus();
							etBankName.setEnabled(false);
							etBankAccountNumber.setEnabled(false);

							setActivationCode.setText("");
							etBankName.setText("");
							etBankAccountNumber.setText("");

							setActivationCode.setHint("Activation code");
							etBankName.setHint("Bank name");
							etBankAccountNumber.setHint("Account number");
							break;
						case R.id.radiobuttonBankTransaction:
							checkedButtonID = 3;
							setActivationCode.setEnabled(false);
							etTransactionID.setEnabled(false);
							etBankName.setEnabled(true);
							etBankName.setFocusableInTouchMode(true);
							etBankName.requestFocus();
							etBankAccountNumber.setEnabled(true);
							setActivationCode.setText("");
							etTransactionID.setText("");

							setActivationCode.setHint("Activation code");
							etTransactionID.setHint("Transaction id");

						}
					}
				});

		btnSubmit.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				switch (checkedButtonID) {
				case 1:
					transactionId = "";
					activationCode = setActivationCode.getText().toString();
					bankName = "";
					accountNumber = "";
					break;

				case 2:

					transactionId = etTransactionID.getText().toString();
					activationCode = "";
					bankName = "";
					accountNumber = "";

					break;
				case 3:

					transactionId = "";
					activationCode = "";
					bankName = etBankName.getText().toString();
					accountNumber = etBankAccountNumber.getText().toString();

					break;
				}

				if (connecctionDetector.isConnectingToInternet()) {

					if (checkedButtonID == 1) {

						if (setActivationCode.getText().toString().equals("")) {
							Toast.makeText(
									BookDetailActivity.this,
									"Please Enter Activation Code and try again!",
									Toast.LENGTH_LONG).show();
						} else {
							try {
								if (mTask2 != null) {
									mTask2.cancel(true);
								}
								mTask2 = new CashDeliveryOptionsTask(
										BookDetailActivity.this,
										book,
										ConstantMessageUrls.PAYMENT_DELIVERY_OPTIONS_CHECK,
										etUserEmail.getText().toString(), true,
										"" + checkedButtonID, book.price,
										book.bd_price, activationCode,
										transactionId, bankName, accountNumber,
										pokaId);
								mTask2.execute();

								dialog.cancel();
							} catch (Exception e) {
								e.printStackTrace();
							}

						}

					} else if (checkedButtonID == 2) {
						if (etTransactionID.getText().toString().equals("")) {
							Toast.makeText(
									BookDetailActivity.this,
									"Please Enter Transaction ID and try again!",
									Toast.LENGTH_LONG).show();
						} else {
							try {
								if (mTask2 != null) {
									mTask2.cancel(true);
								}
								mTask2 = new CashDeliveryOptionsTask(
										BookDetailActivity.this,
										book,
										ConstantMessageUrls.PAYMENT_DELIVERY_OPTIONS_CHECK,
										etUserEmail.getText().toString(), true,
										"" + checkedButtonID, book.price,
										book.bd_price, activationCode,
										transactionId, bankName, accountNumber,
										pokaId);
								mTask2.execute();

								dialog.cancel();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (checkedButtonID == 3) {
						if (etBankName.getText().toString().equals("")) {
							Toast.makeText(BookDetailActivity.this,
									"Please Enter Bank Name and try again!",
									Toast.LENGTH_LONG).show();

						} else if (etBankAccountNumber.getText().toString()
								.equals("")) {
							Toast.makeText(
									BookDetailActivity.this,
									"Please Enter Account Number and try again!",
									Toast.LENGTH_LONG).show();

						} else {
							try {
								if (mTask2 != null) {
									mTask2.cancel(true);
								}
								mTask2 = new CashDeliveryOptionsTask(
										BookDetailActivity.this,
										book,
										ConstantMessageUrls.PAYMENT_DELIVERY_OPTIONS_CHECK,
										etUserEmail.getText().toString(), true,
										"" + checkedButtonID, book.price,
										book.bd_price, activationCode,
										transactionId, bankName, accountNumber,
										pokaId);
								mTask2.execute();

								dialog.cancel();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}

				} else {
					connecctionDetector.connectionAlert();
				}
			}
		});

		try {
			dialog.show();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private CashDeliveryOptionsTask mTask2;

	private void CashDeliveryOptions(final int bundle_id) {
		final Dialog dialog = new Dialog(BookDetailActivity.this);

		dialog.setContentView(R.layout.dialog_cash_payment_new);

		final Button btnSubmit = (Button) dialog.findViewById(R.id.btnSubmit);
		final RadioGroup radioGroup = (RadioGroup) dialog
				.findViewById(R.id.radiogroup);
		setActivationCode = (EditText) dialog
				.findViewById(R.id.etActivationCode);
		final RadioButton radioActivationCode = (RadioButton) dialog
				.findViewById(R.id.radiobuttonActivationCode);
		final RadioButton radioBKashTransaction = (RadioButton) dialog
				.findViewById(R.id.radiobuttonBkashTransaction);
		dialog.findViewById(R.id.radiobuttonBankTransaction);

		final EditText etTransactionID = (EditText) dialog
				.findViewById(R.id.etBkashTransactionID);
		final TextView etUserEmail = (TextView) dialog
				.findViewById(R.id.etEmailAddress);
		final EditText etBankName = (EditText) dialog
				.findViewById(R.id.etBankName);
		final EditText etBankAccountNumber = (EditText) dialog
				.findViewById(R.id.etBankAccountNumber);
		final TextView tvBookName = (TextView) dialog
				.findViewById(R.id.tvBookName);

		final ImageView qRCodeReader = (ImageView) dialog
				.findViewById(R.id.idQRScanner);

		final ImageView bKashHelpLink = (ImageView) dialog
				.findViewById(R.id.idBKashHelp);

		bKashHelpLink.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(BookDetailActivity.this,
						BKashHelpScreen.class);
				i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(i);
			}
		});

		qRCodeReader.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent i = new Intent(BookDetailActivity.this,
						QRScannerActivity.class);
				startActivityForResult(i, 1);
			}
		});

		final BookBundle bundleObject = database.getBundle(bundle_id);

		etUserEmail.setText(getUserEmail().toString());
		etUserEmail.setEnabled(false);
		/** No bundle name in bundle object, so the title value is null */
		tvBookName.setText("");

		radioActivationCode.setChecked(true);
		radioBKashTransaction.setChecked(false);
		//
		setActivationCode.setEnabled(true);
		etTransactionID.setEnabled(false);
		etBankName.setEnabled(false);
		etBankAccountNumber.setEnabled(false);

		radioGroup
				.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(RadioGroup group, int checkedId) {

						switch (checkedId) {
						case R.id.radiobuttonActivationCode:
							checkedButtonID = 1;
							setActivationCode.setEnabled(true);
							setActivationCode.setFocusableInTouchMode(true);
							setActivationCode.requestFocus();
							etTransactionID.setEnabled(false);
							etBankName.setEnabled(false);
							etBankAccountNumber.setEnabled(false);
							etTransactionID.setText("");
							etBankName.setText("");
							etBankAccountNumber.setText("");

							etTransactionID.setHint("Transaction id");
							etBankName.setHint("enter bank name");
							etBankAccountNumber.setHint("enter account number");
							break;
						case R.id.radiobuttonBkashTransaction:
							checkedButtonID = 2;
							setActivationCode.setEnabled(false);
							etTransactionID.setEnabled(true);
							etTransactionID.setFocusableInTouchMode(true);
							etTransactionID.requestFocus();
							etBankName.setEnabled(false);
							etBankAccountNumber.setEnabled(false);

							setActivationCode.setText("");
							etBankName.setText("");
							etBankAccountNumber.setText("");

							setActivationCode.setHint("Activation code");
							etBankName.setHint("enter bank name");
							etBankAccountNumber.setHint("enter account number");
							break;
						case R.id.radiobuttonBankTransaction:
							checkedButtonID = 3;
							setActivationCode.setEnabled(false);
							etTransactionID.setEnabled(false);
							etBankName.setEnabled(true);
							etBankName.setFocusableInTouchMode(true);
							etBankName.requestFocus();
							etBankAccountNumber.setEnabled(true);

							setActivationCode.setText("");
							etTransactionID.setText("");

							setActivationCode.setHint("Activation code");
							etTransactionID.setHint("Transaction id");

						}
					}
				});

		btnSubmit.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				switch (checkedButtonID) {
				case 1:
					transactionId = "";
					activationCode = setActivationCode.getText().toString();
					bankName = "";
					accountNumber = "";
					break;

				case 2:

					transactionId = etTransactionID.getText().toString();
					activationCode = "";
					bankName = "";
					accountNumber = "";

					break;
				case 3:

					transactionId = "";
					activationCode = "";
					bankName = etBankName.getText().toString();
					accountNumber = etBankAccountNumber.getText().toString();

					break;
				}

				if (connecctionDetector.isConnectingToInternet()) {

					if (checkedButtonID == 1) {

						if (setActivationCode.getText().toString().equals("")) {
							Toast.makeText(
									BookDetailActivity.this,
									"Please Enter Activation Code and try again!",
									Toast.LENGTH_LONG).show();
						} else {
							try {

								CashDeliveryOptionsTask optionsTask = new CashDeliveryOptionsTask(
										BookDetailActivity.this,
										ConstantMessageUrls.PAYMENT_DELIVERY_OPTIONS_CHECK,
										etUserEmail.getText().toString(),
										bundle_id, false, "" + checkedButtonID,
										bundleObject.bundle_usd_price,
										bundleObject.bundle_bd_price,
										activationCode, transactionId,
										bankName, accountNumber, pokaId);
								optionsTask.execute();
								dialog.cancel();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}

					} else if (checkedButtonID == 2) {
						if (etTransactionID.getText().toString().equals("")) {
							Toast.makeText(
									BookDetailActivity.this,
									"Please Enter Transaction ID and try again!",
									Toast.LENGTH_LONG).show();
						} else {
							try {
								CashDeliveryOptionsTask optionsTask = new CashDeliveryOptionsTask(
										BookDetailActivity.this,
										ConstantMessageUrls.PAYMENT_DELIVERY_OPTIONS_CHECK,
										etUserEmail.getText().toString(),
										bundle_id, false, "" + checkedButtonID,
										bundleObject.bundle_usd_price,
										bundleObject.bundle_bd_price,
										activationCode, transactionId,
										bankName, accountNumber, pokaId);
								optionsTask.execute();
								dialog.cancel();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					} else if (checkedButtonID == 3) {
						if (etBankName.getText().toString().equals("")) {
							Toast.makeText(BookDetailActivity.this,
									"Please Enter Bank Name and try again!",
									Toast.LENGTH_LONG).show();

						} else if (etBankAccountNumber.getText().toString()
								.equals("")) {
							Toast.makeText(
									BookDetailActivity.this,
									"Please Enter Account Number and try again!",
									Toast.LENGTH_LONG).show();

						} else {
							try {
								CashDeliveryOptionsTask optionsTask = new CashDeliveryOptionsTask(
										BookDetailActivity.this,
										ConstantMessageUrls.PAYMENT_DELIVERY_OPTIONS_CHECK,
										etUserEmail.getText().toString(),
										bundle_id, false, "" + checkedButtonID,
										bundleObject.bundle_usd_price,
										bundleObject.bundle_bd_price,
										activationCode, transactionId,
										bankName, accountNumber, pokaId);
								optionsTask.execute();
								dialog.cancel();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}

				} else {
					connecctionDetector.connectionAlert();
				}
			}
		});

		try {
			dialog.show();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/** Payments the cash for buying books */
	public class CashDeliveryOptionsTask extends AsyncTask<Void, Void, Void> {

		Context context;
		String url;
		String email;
		String code;
		int bundle_id;
		String message;
		boolean bundleOrNot;
		CustomProgressDialog dialog;
		String paymentType, payment, activation_code, bankName, account_number,
				poka_id, transaction_id, bd_payment;

		Book book;

		public CashDeliveryOptionsTask(Context context, String url,
				String email, int bundle_id, boolean bundleOrNot,
				String paymentType, String payment, String bd_payment,
				String activation_code, String transaction_id, String bankName,
				String account_number, String poka_id) {

			this.context = context;
			this.url = url;
			this.email = email;
			this.bundle_id = bundle_id;
			this.bundleOrNot = bundleOrNot;
			this.paymentType = paymentType;
			this.payment = payment;
			this.activation_code = activation_code;
			this.bankName = bankName;
			this.account_number = account_number;
			this.poka_id = poka_id;
			this.transaction_id = transaction_id;
			this.bd_payment = bd_payment;
		}

		public CashDeliveryOptionsTask(Context context, Book book, String url,
				String email, boolean bundleOrNot, String paymentType,
				String payment, String bd_payment, String activation_code,
				String transaction_id, String bankName, String account_number,
				String poka_id) {

			this.context = context;
			this.url = url;
			this.email = email;
			this.bundleOrNot = bundleOrNot;
			this.book = book;
			this.paymentType = paymentType;
			this.payment = payment;
			this.activation_code = activation_code;
			this.bankName = bankName;
			this.account_number = account_number;
			this.poka_id = poka_id;
			this.transaction_id = transaction_id;
			this.bd_payment = bd_payment;

		}

		@Override
		protected void onPreExecute() {

			super.onPreExecute();

			try {
				dialog = new CustomProgressDialog(BookDetailActivity.this);
				dialog.setTitle(R.string.app_name);
				dialog.setMessage("Please wait...");
				dialog.setCancelable(true);
				dialog.setIndeterminate(true);

				dialog.show();
			} catch (Exception exception) {
				exception.printStackTrace();
			}
		}

		@Override
		protected Void doInBackground(Void... params) {

			String json = null;

			json = JsonBuilderForPaymentOptions(email, bundle_id, book,
					bundleOrNot, paymentType, payment, bd_payment,
					activation_code, transaction_id, bankName, account_number,
					poka_id);

			StringBuilder sb;
			InputStream is = null;
			String result = "";

			// System.out.println("App Side JSON: " + json);
			List<NameValuePair> orderJson = new ArrayList<NameValuePair>();

			orderJson.add(new BasicNameValuePair("payment_process", json));

			// System.out.println("final: " + orderJson.toString());

			/* http post */
			try {
				HttpClient httpclient = new DefaultHttpClient();
				HttpPost httppost = new HttpPost(url);

				httppost.setEntity(new UrlEncodedFormEntity(orderJson));
				HttpResponse response = httpclient.execute(httppost);
				HttpEntity entity = response.getEntity();
				is = entity.getContent();

			} catch (Exception e) {
				// Log.e("log_tag", "Error in http connection" + e.toString());
			}

			/* convert response to string */
			try {
				BufferedReader reader = new BufferedReader(
						new InputStreamReader(is, "iso-8859-1"), 8);
				sb = new StringBuilder();
				sb.append(reader.readLine() + "\n");
				String line = "0";
				while ((line = reader.readLine()) != null) {
					sb.append(line + "\n");
				}
				is.close();
				result = sb.toString();
				System.out.println("Returned JSON: " + result);
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				JSONObject jsonResult = new JSONObject(result);
				message = jsonResult.getString("message");
				if (message.equals("success")) {
					// System.out.println("status message: " + message);
				}
			} catch (Exception Ex) {
				Ex.printStackTrace();
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {

			super.onPostExecute(result);
			try {
				if (dialog != null) {
					if (dialog.isShowing())
						try {
							dialog.dismiss();
						} catch (Exception e) {
							e.printStackTrace();
						}
					dialog = null;
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				if (message.equals("success")) {
					if (!bundleOrNot) {
						database.updateBundleBooks(bundle_id);
					} else {
						downloadManager(book, BookDetailActivity.this);
					}
				} else {
					if (message != null) {
						Toast.makeText(context, " " + message,
								Toast.LENGTH_LONG).show();
					} else {
						Toast.makeText(
								context,
								"Something went wrong! Please retry in few minutes!",
								Toast.LENGTH_LONG).show();
					}
					if (!bundleOrNot) {
						CashDeliveryOptions(bundle_id);
					} else {
						Log.e("", "HERE 34");
						CashDeliveryOptions(book);
					}
				}
			} catch (Exception E) {
				E.printStackTrace();
				Toast.makeText(context,
						"Something went wrong! Please retry in few minutes!",
						Toast.LENGTH_LONG).show();
			}
		}
	}

	/** Builds json for payment options */
	private String JsonBuilderForPaymentOptions(String email, int bundle_id,
			Book book, boolean bundleOrNot, String payment_type,
			String payment, String bd_payment, String activation_code,
			String bKashTransactionId, String bank_name,
			String bank_acc_number, String poka_id) {

		String jsonstring = "";
		JSONObject json = new JSONObject();
		JSONObject main = new JSONObject();
		String dID = "35" + Build.BOARD.length() % 10 + Build.BRAND.length()
				% 10 + Build.CPU_ABI.length() % 10 + Build.DEVICE.length() % 10
				+ Build.DISPLAY.length() % 10 + Build.HOST.length() % 10
				+ Build.ID.length() % 10 + Build.MANUFACTURER.length() % 10
				+ Build.MODEL.length() % 10 + Build.PRODUCT.length() % 10
				+ Build.TAGS.length() % 10 + Build.TYPE.length() % 10
				+ Build.USER.length() % 10;
		try {

			if (bundleOrNot) {
				main.put("bundle_id", book.is_free);
				main.put("book_id", book.book_id);

			} else {
				main.put("bundle_id", bundle_id);
				main.put("book_id", "0");
			}

			main.put("payment_type", payment_type);
			main.put("payment", payment);
			main.put("bdt_price", bd_payment);
			main.put("device_email", email);
			main.put("activation_code", activation_code);
			main.put("bkash_transaction_id", bKashTransactionId);
			main.put("device_id", dID);
			main.put("bank_name", bank_name);
			main.put("bank_account_number", bank_acc_number);
			main.put("poka_id", poka_id);
			json.put("payment_json", main);
			jsonstring = json.toString();
			// System.out.println("Payment JSON:::" + jsonstring);
		} catch (Exception Ex) {
			Ex.printStackTrace();
		}
		return jsonstring;
	}

	private void initUI() {
		// layoutBookDetail = (LinearLayout)
		// findViewById(R.id.layout_book_detail);
		// layoutPaymentOptions = (LinearLayout)
		// findViewById(R.id.layout_payment_options);

		try {
			btnGooglePlay = (Button) findViewById(R.id.btnDetailBuyFromGooglePlay);
			btnGooglePlay.setOnClickListener(this);

			imageLoader = ImageLoader.getInstance();
			imageLoader.init(ImageLoaderConfiguration
					.createDefault(BookDetailActivity.this));

			displayOptions = new DisplayImageOptions.Builder()
					.showImageOnLoading(R.drawable.ic_stub)
					.showImageForEmptyUri(R.drawable.ic_empty)
					.showImageOnFail(R.drawable.ic_error).cacheInMemory(false)
					.cacheOnDisc(true).displayer(new SimpleBitmapDisplayer())
					.build();

			imgBook = (ImageView) findViewById(R.id.imgDetailImage);
			// imgBookPage1 = (ImageView) findViewById(R.id.imgBookPage1);
			// imgBookPage2 = (ImageView) findViewById(R.id.imgBookPage2);
			// imgBookPage3 = (ImageView) findViewById(R.id.imgBookPage3);
			btnCashOnDelivery = (Button) findViewById(R.id.btnDetailBuyCashOnDelivery);
			btnGooglePlay = (Button) findViewById(R.id.btnDetailBuyFromGooglePlay);
			btnDownload = (Button) findViewById(R.id.btnDetailDownload);
			tvBookAuthor = (TextView) findViewById(R.id.tvDetailAuthor);
			tvBookPrice = (TextView) findViewById(R.id.tvDetailPrice);
			tvBookName = (TextView) findViewById(R.id.tvDetailBookName);
			tvTotalPages = (TextView) findViewById(R.id.tvDetailTotalPages);
			tvSamplePages = (TextView) findViewById(R.id.tvSampleLink);

			rbRating = (RatingBar) findViewById(R.id.rbRating);
			tvBookPublisher = (TextView) findViewById(R.id.tvDetailPublisher);
			tvBookCategory = (TextView) findViewById(R.id.tvDetailCategory);

			if (book.is_free == 1
					|| (book.is_free > 1 && book.price.equals("0") && book.bd_price
							.equals("0")) || isBookAlreadyPurchased) {
				btnGooglePlay.setVisibility(Button.GONE);
				btnCashOnDelivery.setVisibility(Button.GONE);
				btnDownload.setVisibility(Button.VISIBLE);
			} else if (book.is_free == 0
					|| (book.is_free > 1 && (!book.price.equals("0") || !book.bd_price
							.equals("0")))) {
				btnGooglePlay.setVisibility(Button.VISIBLE);
				btnCashOnDelivery.setVisibility(Button.VISIBLE);
				btnDownload.setVisibility(Button.GONE);
			}

			tvBookName.setText(book.title);
			tvBookAuthor.setText("লেখক : " + book.writer_name);
			if (book.price.equals("0") && book.bd_price.equals("0")) {
				tvBookPrice.setText("মূল্য : ফ্রি");
			} else {
				tvBookPrice.setText("মূল্য : $ " + book.price + " / " + "৳ "
						+ book.bd_price);
			}

			if (!TextUtils.isEmpty(book.publisher_name))
				tvBookPublisher.setText("প্রকাশক : " + book.publisher_name);
			else
				tvBookPublisher.setVisibility(View.GONE);
			if (!TextUtils.isEmpty(book.category_name))
				tvBookCategory.setText("বইয়ের ধরণ : " + book.category_name);
			else
				tvBookCategory.setVisibility(View.GONE);

			tvTotalPages.setText("পৃষ্ঠা সংখ্যা : " + book.total_pages);
			rbRating.setRating(Float.parseFloat(book.rating));

			tvSamplePages.setPaintFlags(tvSamplePages.getPaintFlags()
					| Paint.UNDERLINE_TEXT_FLAG);

			imageLoader.displayImage(ConstantMessageUrls.IMAGE
					+ book.front_image, imgBook, displayOptions,
					animateFirstListener);
			// imageLoader.displayImage(ConstantMessageUrls.BOOK_PREVIEW_IMAGE
			// + book.preview_image_1, imgBookPage1, displayOptions,
			// animateFirstListener);
			// imageLoader.displayImage(ConstantMessageUrls.BOOK_PREVIEW_IMAGE
			// + book.preview_image_2, imgBookPage2, displayOptions,
			// animateFirstListener);
			// imageLoader.displayImage(ConstantMessageUrls.BOOK_PREVIEW_IMAGE
			// + book.preview_image_3, imgBookPage3, displayOptions,
			// animateFirstListener);

			btnGooglePlay.setOnClickListener(this);
			btnCashOnDelivery.setOnClickListener(this);
			btnDownload.setOnClickListener(this);
			tvSamplePages.setOnClickListener(this);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private static boolean isRooted() {
		return findBinary("su");
	}

	public static boolean findBinary(String binaryName) {
		boolean found = false;
		if (!found) {
			String[] places = { "/sbin/", "/system/bin/", "/system/xbin/",
					"/data/local/xbin/", "/data/local/bin/",
					"/system/sd/xbin/", "/system/bin/failsafe/", "/data/local/" };
			for (String where : places) {
				if (new File(where + binaryName).exists()) {
					found = true;
					break;
				}
			}
		}
		return found;
	}

	private boolean appInstalledOrNot(String uri) {
		PackageManager pm = getPackageManager();
		boolean app_installed = false;
		try {
			pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
			app_installed = true;
		} catch (PackageManager.NameNotFoundException e) {
			app_installed = false;
		}
		return app_installed;
	}
}
