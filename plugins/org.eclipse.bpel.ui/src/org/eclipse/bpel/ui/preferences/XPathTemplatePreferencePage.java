package org.eclipse.bpel.ui.preferences;

import org.eclipse.swt.graphics.Font;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.preference.*;
import org.eclipse.ui.texteditor.templates.TemplatePreferencePage;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.bpel.ui.BPELUIPlugin;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.SourceViewer;
import org.eclipse.jface.text.source.SourceViewerConfiguration;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.SWT;
import org.eclipse.jface.text.templates.persistence.TemplateStore;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.ui.editors.text.templates.ContributionContextTypeRegistry;

import org.eclipse.bpel.ui.editors.xpath.ColorManager;
import org.eclipse.bpel.ui.editors.xpath.XPathSourceViewerConfiguration;
import org.eclipse.bpel.ui.editors.xpath.templates.XPathEditorTemplateAccess;

/*
 * Preference page for defining templates used by the XPath Expression Editor
 * within BPEL.
 */
public class XPathTemplatePreferencePage extends TemplatePreferencePage	implements IWorkbenchPreferencePage {
	private ColorManager colorManager;
	
	public XPathTemplatePreferencePage() {
        setPreferenceStore(BPELUIPlugin.getDefault().getPreferenceStore());
        setContextTypeRegistry(XPathEditorTemplateAccess.getDefault().getContextTypeRegistry());
        //XPathEditorTemplateAccess.getDefault().getContextTypeRegistry().addContextType("xpath");
        //XPathEditorTemplateAccess.getDefault().getContextTypeRegistry().addContextType("jscript");
        setTemplateStore(XPathEditorTemplateAccess.getDefault().getTemplateStore());
        colorManager = null;
	}

	protected SourceViewer createViewer(Composite parent) {
		SourceViewer viewer = new SourceViewer(parent, null, null, false, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		Font font= JFaceResources.getFont(JFaceResources.TEXT_FONT);
		viewer.getTextWidget().setFont(font);  
		
		colorManager = new ColorManager();
		XPathSourceViewerConfiguration configuration = new XPathSourceViewerConfiguration(colorManager); 
	
		viewer.configure(configuration);
		IDocument document= new Document();
		viewer.setDocument(document);
		return viewer;
	}

    /* (non-Javadoc)
     * @see org.eclipse.jface.preference.IPreferencePage#performOk()
     */
    public boolean performOk() {
	  boolean ok = super.performOk();
	  BPELUIPlugin.getDefault().savePluginPreferences();
	  if (colorManager != null)
		  colorManager.dispose();
	  return ok;
    }

	@Override
	public boolean performCancel() {
		boolean cancel = super.performCancel();
		if (colorManager != null)
			colorManager.dispose();
		return cancel;
	}
    
    
}