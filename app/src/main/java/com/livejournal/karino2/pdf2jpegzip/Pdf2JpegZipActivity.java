package com.livejournal.karino2.pdf2jpegzip;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;


public class Pdf2JpegZipActivity extends ActionBarActivity {

    final int DIALOG_ID_PARSE_PDF_TARGET = 1;
    final int DIALOG_ID_PARSE_FOLDER_TARGET = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf2_jpeg_zip);


        Intent intent = getIntent();
        Uri uri;

        if(intent == null) {
            showMessage("intent null, do nothing.");
            finish();
            return;
        }


        if (intent.getAction().equals(Intent.ACTION_SEND)) {
            uri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                showMessage("not supported. getParcelableExtra fail.");
                finish();
                return;
            }
        } else if (intent.getAction().equals(Intent.ACTION_VIEW)) {
            uri = intent.getData();
        } else if(intent.getAction().equals(Intent.ACTION_MAIN)) {
            // for debug
            uri = Uri.fromFile(new File("/storage/extSdCard/2_country/arabic/test.pdf"));
        } else {
            showMessage("unknown action. " + intent.getAction());
            finish();
            return;
        }

        File target = new File(uri.getPath());
        if(target.isDirectory()) {
            startParseTaskForFolder(target);
            return;
        } else {
            if(!intent.getAction().equals(Intent.ACTION_MAIN) && !intent.getType().equals("application/pdf")) {
                showMessage("Unknown mime type: " + intent.getType());
                finish();
                return;
            }
            startParseTaskForPdf(target);
        }

    }


    class PdfParseTask extends AsyncTask<File, Integer, Boolean> {
        Context context;
        ProgressDialog progress;
        boolean isTargetPdf;
        PdfParseTask(ProgressDialog prog, Context ctx, boolean isTargetPdf)
        {
            progress = prog;
            context = ctx;
            this.isTargetPdf = isTargetPdf;
        }

        public File[] listPdfFiles(File folder) throws IOException {
            final String ext = ".pdf";
            return listFiles(folder, ext);
        }

        private File[] listFiles(File folder, final String ext) {
            File[] slideFiles = folder.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    if (filename.endsWith(ext))
                        return true;
                    return false;
                }
            });
            Arrays.sort(slideFiles, new Comparator<File>() {
                public int compare(File f1, File f2) {
                    return f1.getName().compareTo(f2.getName());
                }

            });

            return slideFiles;
        }


        String errorMessage = "";
        File outputFolder;

        @Override
        protected Boolean doInBackground(File... params) {
            try {
                extractImagesToOutputFolder(params);
                zipFolder(outputFolder);
                removeFolder(outputFolder);
                return true;
            } catch (IOException e) {
                errorMessage = e.getMessage();
                return false;
            }

        }

        private void removeFolder(File folder) {
            String[] children = folder.list();
            for (int i = 0; i < children.length; i++) {
                new File(folder, children[i]).delete();
            }
            folder.delete();
        }

        private void zipFolder(File folder) throws IOException {
            ZipOutputStream outZip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(folder.getAbsolutePath() + ".zip")));
            final int BUF_SIZE = 4096;
            try {
                byte[] buf = new byte[BUF_SIZE];
                for(File jpegFile: listFiles(folder, ".jpg")) {
                    ZipEntry entry = new ZipEntry(jpegFile.getName());
                    outZip.putNextEntry(entry);

                    BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(jpegFile));
                    try {
                        int readBytes = 0;
                        while ((readBytes = inputStream.read(buf)) > 0) {
                            outZip.write(buf, 0, readBytes);
                        }
                    }finally {
                        inputStream.close();
                    }
                }
            } finally {
                outZip.close();
            }

        }
        private void extractImagesToOutputFolder(File[] params) throws IOException {
            if(isTargetPdf) {
                File pdfFile = params[0];
                parseOnePdfOnly(pdfFile);
            } else {
                File targetFolder = params[0];
                parseAllPdfInsideFolder(targetFolder);
            }
        }

        private void parseAllPdfInsideFolder(File targetFolder) throws IOException {
            outputFolder = new File(targetFolder, targetFolder.getName());
            if (!outputFolder.mkdir()) {
                errorMessage = "Create dir fail: " + outputFolder.getAbsolutePath();
                throw new IOException(errorMessage);
            }


            PdfParser parser = new PdfParser(outputFolder);
            File[] files = listPdfFiles(targetFolder);
            progress.setMax(files.length);
            int count = 0;
            for(File pdfFile : files) {
                parser.doOnePdf(pdfFile);
                publishProgress(count++);
            }
        }

        private void parseOnePdfOnly(File pdfFile) throws IOException {
            String baseName = PdfParser.getBaseName(pdfFile);


            outputFolder = new File(pdfFile.getParentFile(), baseName);
            if (!outputFolder.mkdir()) {
                errorMessage = "Create dir fail: " + outputFolder.getAbsolutePath();
                throw new IOException(errorMessage);
            }


            PdfParser parser = new PdfParser(outputFolder);
            parser.doOnePdf(pdfFile);
        }

        @Override
        protected void onPostExecute(Boolean isSuccess) {
            if(!isSuccess) {
                showMessage(errorMessage);
            }
            progress.dismiss();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progress.setProgress(values[0]);
        }
    }

    PdfParseTask parseTask;

    @Nullable
    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch(id) {
            case DIALOG_ID_PARSE_PDF_TARGET:
                return startParse(args, true);
            case DIALOG_ID_PARSE_FOLDER_TARGET:
                return startParse(args, false);

        }
        return super.onCreateDialog(id, args);
    }

    private Dialog startParse(Bundle args, boolean isTargetPdf) {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Parsing...");
        progress.setCancelable(true);

        parseTask = new PdfParseTask(progress, this, isTargetPdf);

        parseTask.execute(new File(args.getString("PATH")));
        progress.setOnCancelListener(new DialogInterface.OnCancelListener() {

            @Override
            public void onCancel(DialogInterface dialog) {
                parseTask.cancel(false);
            }
        });


        return progress;
    }

    public void startParseTaskForFolder(File folder) {
        Bundle bundle = new Bundle();
        bundle.putString("PATH", folder.getAbsolutePath());
        showDialog(DIALOG_ID_PARSE_FOLDER_TARGET, bundle);
    }


    public void startParseTaskForPdf(File pdfFile) {
        Bundle bundle = new Bundle();
        bundle.putString("PATH", pdfFile.getAbsolutePath());
        showDialog(DIALOG_ID_PARSE_PDF_TARGET, bundle);
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_pdf2_jpeg_zip, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
