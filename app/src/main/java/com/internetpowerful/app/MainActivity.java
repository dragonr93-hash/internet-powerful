package com.internetpowerful.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.Settings;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> filePathCallback;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_FILE_REQUEST = 1002;

    private byte[] pendingFileBytes;
    private String pendingFileName;
    private String pendingMimeType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                return openExternalUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url) {

                return openExternalUrl(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                /*
                 * La aplicación utiliza window.print().
                 * Aquí lo conectamos con el sistema de impresión de Android.
                 */
                view.evaluateJavascript(
                        "(function() {" +
                        "window.print = function() {" +
                        "if (window.Android) Android.printPage();" +
                        "};" +
                        "})();",
                        null
                );

                /*
                 * Forzamos que el respaldo local utilice
                 * nuestro sistema nativo de guardado.
                 */
                view.evaluateJavascript(
                        "(function() {" +
                        "try {" +
                        "Object.defineProperty(navigator, 'share', {" +
                        "value: undefined," +
                        "configurable: true" +
                        "});" +
                        "} catch(e) {}" +
                        "})();",
                        null
                );
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            /*
             * ALERT de JavaScript
             */
            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    final JsResult result) {

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("Aceptar",
                                (dialog, which) -> result.confirm())
                        .setOnCancelListener(
                                dialog -> result.cancel())
                        .show();

                return true;
            }

            /*
             * CONFIRM de JavaScript
             */
            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    final JsResult result) {

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setNegativeButton("Cancelar",
                                (dialog, which) -> result.cancel())
                        .setPositiveButton("Aceptar",
                                (dialog, which) -> result.confirm())
                        .setOnCancelListener(
                                dialog -> result.cancel())
                        .show();

                return true;
            }

            /*
             * PROMPT de JavaScript
             */
            @Override
            public boolean onJsPrompt(
                    WebView view,
                    String url,
                    String message,
                    String defaultValue,
                    final JsPromptResult result) {

                final EditText input = new EditText(MainActivity.this);
                input.setSingleLine(false);
                input.setText(defaultValue);

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(input)
                        .setNegativeButton("Cancelar",
                                (dialog, which) -> result.cancel())
                        .setPositiveButton("Aceptar",
                                (dialog, which) ->
                                        result.confirm(
                                                input.getText().toString()))
                        .setOnCancelListener(
                                dialog -> result.cancel())
                        .show();

                return true;
            }

            /*
             * Selector de archivos de Android.
             * Esto permite restaurar respaldos JSON.
             */
            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }

                MainActivity.this.filePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                } catch (ActivityNotFoundException e) {

                    MainActivity.this.filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo abrir el selector de archivos",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }

                return true;
            }
        });

        /*
         * Permite que los enlaces de descarga pasen por Android.
         */
        webView.setDownloadListener(
                new DownloadListener() {
                    @Override
                    public void onDownloadStart(
                            String url,
                            String userAgent,
                            String contentDisposition,
                            String mimetype,
                            long contentLength) {

                        try {
                            Intent intent =
                                    new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(url));

                            startActivity(intent);

                        } catch (Exception e) {

                            Toast.makeText(
                                    MainActivity.this,
                                    "No se pudo abrir la descarga",
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    }
                }
        );

        /*
         * Puente entre JavaScript y Android.
         */
        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }

    /*
     * Manejo de WhatsApp, teléfono, correo y enlaces externos.
     */
    private boolean openExternalUrl(String url) {

        if (url == null) {
            return false;
        }

        try {

            if (url.startsWith("whatsapp://")
                    || url.startsWith("https://wa.me/")
                    || url.startsWith("https://api.whatsapp.com/")) {

                Intent intent =
                        new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));

                startActivity(intent);
                return true;
            }

            if (url.startsWith("tel:")) {

                Intent intent =
                        new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse(url));

                startActivity(intent);
                return true;
            }

            if (url.startsWith("mailto:")) {

                Intent intent =
                        new Intent(Intent.ACTION_SENDTO);
                intent.setData(Uri.parse(url));

                startActivity(intent);
                return true;
            }

            if (url.startsWith("http://")
                    || url.startsWith("https://")) {

                Intent intent =
                        new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));

                startActivity(intent);
                return true;
            }

        } catch (Exception e) {

            Toast.makeText(
                    this,
                    "No se pudo abrir el enlace",
                    Toast.LENGTH_SHORT
            ).show();

            return true;
        }

        return false;
    }

    /*
     * Puente Android <-> JavaScript.
     */
    private class AndroidBridge {

        @JavascriptInterface
        public void printPage() {

            runOnUiThread(() -> {

                try {

                    PrintManager printManager =
                            (PrintManager) getSystemService(
                                    Context.PRINT_SERVICE
                            );

                    PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter(
                                    "Internet Powerful"
                            );

                    PrintAttributes attributes =
                            new PrintAttributes.Builder()
                                    .setMediaSize(
                                            PrintAttributes.MediaSize.NA_LETTER
                                    )
                                    .setMinMargins(
                                            PrintAttributes.Margins.NO_MARGINS
                                    )
                                    .build();

                    printManager.print(
                            "Internet Powerful",
                            adapter,
                            attributes
                    );

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo abrir la impresión",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }

        /*
         * Recibe un archivo en Base64 desde JavaScript
         * y abre el selector de guardado de Android.
         */
        @JavascriptInterface
        public void saveBase64File(
                String fileName,
                String mimeType,
                String base64Data) {

            runOnUiThread(() -> {

                try {

                    pendingFileBytes =
                            Base64.getDecoder().decode(base64Data);

                    pendingFileName = fileName;
                    pendingMimeType = mimeType;

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_CREATE_DOCUMENT
                            );

                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );

                    intent.setType(
                            mimeType != null
                                    ? mimeType
                                    : "application/octet-stream"
                    );

                    intent.putExtra(
                            Intent.EXTRA_TITLE,
                            fileName
                    );

                    startActivityForResult(
                            intent,
                            CREATE_FILE_REQUEST
                    );

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo preparar el archivo",
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        /*
         * Resultado del selector de archivos.
         */
        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (filePathCallback == null) {
                return;
            }

            Uri[] results = null;

            if (resultCode == RESULT_OK
                    && data != null) {

                Uri uri = data.getData();

                if (uri != null) {
                    results = new Uri[]{uri};
                }
            }

            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }

        /*
         * Resultado de guardar un respaldo.
         */
        if (requestCode == CREATE_FILE_REQUEST) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pendingFileBytes != null) {

                Uri uri = data.getData();

                try {

                    FileOutputStream outputStream =
                            (FileOutputStream)
                                    getContentResolver()
                                            .openAssetFileDescriptor(
                                                    uri,
                                                    "w"
                                            )
                                            .createOutputStream();

                    outputStream.write(
                            pendingFileBytes
                    );

                    outputStream.flush();
                    outputStream.close();

                    Toast.makeText(
                            this,
                            "Respaldo guardado correctamente",
                            Toast.LENGTH_LONG
                    ).show();

                } catch (IOException e) {

                    Toast.makeText(
                            this,
                            "Error al guardar el respaldo",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }

            pendingFileBytes = null;
            pendingFileName = null;
            pendingMimeType = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
