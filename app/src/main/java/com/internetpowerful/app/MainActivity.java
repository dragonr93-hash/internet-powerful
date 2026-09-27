package com.internetpowerful.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintManager;
import android.print.PrintDocumentAdapter;
import android.util.Base64;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.auth.api.identity.AuthorizationClient;
import com.google.android.gms.auth.api.identity.AuthorizationRequest;
import com.google.android.gms.auth.api.identity.AuthorizationResult;
import com.google.android.gms.auth.api.identity.Identity;
import com.google.android.gms.auth.api.identity.RevokeAccessRequest;
import com.google.android.gms.common.api.Scope;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class MainActivity extends Activity {

    private WebView webView;

    private ValueCallback<Uri[]> fileChooserCallback;

    private byte[] pendingFileBytes;
    private String pendingFileName;
    private String pendingFileMime;

    private static final int FILE_CHOOSER_REQUEST = 1001;
    private static final int CREATE_FILE_REQUEST = 1002;

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

        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {

                if (fileChooserCallback != null) {
                    fileChooserCallback.onReceiveValue(null);
                }

                fileChooserCallback = filePathCallback;

                try {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                    return true;

                } catch (Exception e) {

                    fileChooserCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo abrir el selector de archivos",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }
            }

            @Override
            public boolean onJsAlert(
                    WebView view,
                    String url,
                    String message,
                    android.webkit.JsResult result) {

                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(
                                "Aceptar",
                                (dialog, which) -> result.confirm()
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }

            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    android.webkit.JsResult result) {

                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setNegativeButton(
                                "Cancelar",
                                (dialog, which) -> result.cancel()
                        )
                        .setPositiveButton(
                                "Aceptar",
                                (dialog, which) -> result.confirm()
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }

            @Override
            public boolean onJsPrompt(
                    WebView view,
                    String url,
                    String message,
                    String defaultValue,
                    android.webkit.JsPromptResult result) {

                final android.widget.EditText input =
                        new android.widget.EditText(MainActivity.this);

                input.setSingleLine(true);
                input.setText(defaultValue);

                android.widget.FrameLayout container =
                        new android.widget.FrameLayout(MainActivity.this);

                int padding = 40;

                container.setPadding(
                        padding,
                        0,
                        padding,
                        0
                );

                container.addView(input);

                new android.app.AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setView(container)
                        .setNegativeButton(
                                "Cancelar",
                                (dialog, which) -> result.cancel()
                        )
                        .setPositiveButton(
                                "Aceptar",
                                (dialog, which) ->
                                        result.confirm(
                                                input.getText().toString()
                                        )
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request) {

                Uri uri = request.getUrl();

                if (uri == null) {
                    return false;
                }

                String scheme = uri.getScheme();

                if (scheme == null) {
                    return false;
                }

                /*
                 * Abrimos enlaces externos con Android.
                 */
                if (scheme.equalsIgnoreCase("http")
                        || scheme.equalsIgnoreCase("https")
                        || scheme.equalsIgnoreCase("whatsapp")
                        || scheme.equalsIgnoreCase("tel")
                        || scheme.equalsIgnoreCase("mailto")) {

                    try {

                        Intent intent =
                                new Intent(
                                        Intent.ACTION_VIEW,
                                        uri
                                );

                        startActivity(intent);

                        return true;

                    } catch (Exception e) {

                        Toast.makeText(
                                MainActivity.this,
                                "No se pudo abrir el enlace",
                                Toast.LENGTH_SHORT
                        ).show();

                        return true;
                    }
                }

                return false;
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url) {

                super.onPageFinished(view, url);

                /*
                 * Conectamos window.print() con Android.
                 */
                view.evaluateJavascript(
                        "window.print=function(){" +
                                "if(window.Android){" +
                                "Android.printPage();" +
                                "}" +
                                "};",
                        null
                );

                /*
                 * V45 intenta utilizar navigator.share()
                 * para el respaldo.
                 *
                 * En Android queremos utilizar nuestro
                 * selector de archivos nativo.
                 */
                view.evaluateJavascript(
                        "(function(){" +
                                "try{" +
                                "Object.defineProperty(" +
                                "navigator,'share'," +
                                "{value:undefined,configurable:true}" +
                                ");" +
                                "}catch(e){}" +
                                "})();",
                        null
                );

                /*
                 * INTERCEPTOR DEL RESPALDO LOCAL
                 *
                 * V45 crea un enlace <a download> y después
                 * ejecuta a.click() mediante JavaScript.
                 *
                 * Por eso interceptamos directamente
                 * HTMLAnchorElement.prototype.click.
                 */
                view.evaluateJavascript(
                        "(function(){" +

                                "if(window.__androidBackupBridge)return;" +

                                "window.__androidBackupBridge=true;" +

                                "var originalClick=" +
                                "HTMLAnchorElement.prototype.click;" +

                                "HTMLAnchorElement.prototype.click=" +
                                "function(){" +

                                "var a=this;" +

                                "if(a && " +
                                "a.hasAttribute('download')" +
                                " && a.href" +
                                " && a.href.indexOf('blob:')===0){" +

                                "var href=a.href;" +

                                "var name=" +
                                "a.getAttribute('download')||" +
                                "'Internet-Powerful-Respaldo.json';" +

                                "fetch(href)" +

                                ".then(function(r){" +
                                "return r.blob();" +
                                "})" +

                                ".then(function(blob){" +

                                "var reader=" +
                                "new FileReader();" +

                                "reader.onloadend=function(){" +

                                "var result=reader.result;" +

                                "var comma=result.indexOf(',');" +

                                "var base64=" +
                                "result.substring(comma+1);" +

                                "Android.saveBase64File(" +
                                "name," +
                                "blob.type||'application/json'," +
                                "base64" +
                                ");" +

                                "};" +

                                "reader.readAsDataURL(blob);" +

                                "})" +

                                ".catch(function(){" +

                                "Android.showToast(" +
                                "'No se pudo preparar el respaldo'" +
                                ");" +

                                "});" +

                                "return;" +

                                "}" +

                                "return originalClick.call(this);" +

                                "};" +

                                "})();",
                        null
                );
            }
        });

        /*
         * Capturamos descargas normales.
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
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse(url)
                                    );

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
         * Puente JavaScript -> Android.
         */
        webView.addJavascriptInterface(
                new AndroidBridge(),
                "Android"
        );

        /*
         * Cargamos la versión V45 incluida
         * dentro de la aplicación.
         */
        webView.loadUrl(
                "file:///android_asset/index.html"
        );
    }

    /*
     * Puente utilizado por JavaScript.
     */
    public class AndroidBridge {

        @JavascriptInterface
        public void showToast(final String message) {

            runOnUiThread(() -> {

                Toast.makeText(
                        MainActivity.this,
                        message,
                        Toast.LENGTH_LONG
                ).show();

            });
        }

        @JavascriptInterface
        public void printPage() {

            runOnUiThread(() -> {

                try {

                    PrintManager printManager =
                            (PrintManager)
                                    getSystemService(
                                            PRINT_SERVICE
                                    );

                    PrintDocumentAdapter adapter =
                            webView.createPrintDocumentAdapter(
                                    "Internet Powerful"
                            );

                    printManager.print(
                            "Internet Powerful",
                            adapter,
                            null
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

        @JavascriptInterface
        public void saveBase64File(
                String fileName,
                String mimeType,
                String base64) {

            try {

                pendingFileBytes =
                        Base64.decode(
                                base64,
                                Base64.DEFAULT
                        );

                pendingFileName = fileName;
                pendingFileMime = mimeType;

            } catch (Exception e) {

                runOnUiThread(() ->
                        Toast.makeText(
                                MainActivity.this,
                                "No se pudo preparar el archivo",
                                Toast.LENGTH_LONG
                        ).show()
                );

                return;
            }

            /*
             * MUY IMPORTANTE:
             *
             * JavaScriptInterface puede ejecutarse
             * en un hilo diferente al hilo principal.
             *
             * Por eso abrimos el selector de archivos
             * dentro de runOnUiThread().
             */
            runOnUiThread(() -> {

                try {

                    Intent intent =
                            new Intent(
                                    Intent.ACTION_CREATE_DOCUMENT
                            );

                    intent.addCategory(
                            Intent.CATEGORY_OPENABLE
                    );

                    intent.setType(
                            pendingFileMime != null
                                    ? pendingFileMime
                                    : "application/json"
                    );

                    intent.putExtra(
                            Intent.EXTRA_TITLE,
                            pendingFileName != null
                                    ? pendingFileName
                                    : "Internet-Powerful-Respaldo.json"
                    );

                    startActivityForResult(
                            intent,
                            CREATE_FILE_REQUEST
                    );

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo abrir la descarga",
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
         * Resultado del selector para RESTAURAR respaldo.
         */
        if (requestCode == FILE_CHOOSER_REQUEST) {

            if (fileChooserCallback == null) {
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

            fileChooserCallback.onReceiveValue(results);

            fileChooserCallback = null;

            return;
        }

        /*
         * Resultado del selector para GUARDAR respaldo.
         */
        if (requestCode == CREATE_FILE_REQUEST) {

            if (resultCode == RESULT_OK
                    && data != null
                    && data.getData() != null
                    && pendingFileBytes != null) {

                Uri uri = data.getData();

                try {

                    OutputStream outputStream =
                            getContentResolver()
                                    .openOutputStream(uri);

                    if (outputStream != null) {

                        outputStream.write(
                                pendingFileBytes
                        );

                        outputStream.flush();
                        outputStream.close();

                        Toast.makeText(
                                MainActivity.this,
                                "Respaldo guardado correctamente",
                                Toast.LENGTH_LONG
                        ).show();
                    }

                } catch (Exception e) {

                    Toast.makeText(
                            MainActivity.this,
                            "No se pudo guardar el respaldo",
                            Toast.LENGTH_LONG
                    ).show();
                }

            } else {

                Toast.makeText(
                        MainActivity.this,
                        "Guardado cancelado",
                        Toast.LENGTH_SHORT
                ).show();
            }

            pendingFileBytes = null;
            pendingFileName = null;
            pendingFileMime = null;
        }
    }

    @Override
    public void onBackPressed() {

        if (webView != null
                && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }
}
