package com.jopdesign.microcode.editor;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;
import org.eclipse.ui.editors.text.FileDocumentProvider;

import com.jopdesign.microcode.syntax.MicrocodePartitionScanner;

/**
 * Document provider for microcode files. Sets up document partitioning
 * so comments, preprocessor directives, and code are handled separately.
 */
public class MicrocodeDocumentProvider extends FileDocumentProvider {

	@Override
	protected IDocument createDocument(Object element) throws CoreException {
		IDocument document = super.createDocument(element);
		if (document instanceof IDocumentExtension3 ext3) {
			IDocumentPartitioner partitioner = new FastPartitioner(
					new MicrocodePartitionScanner(),
					MicrocodePartitionScanner.PARTITION_TYPES);
			partitioner.connect(document);
			ext3.setDocumentPartitioner(
					MicrocodePartitionScanner.MICROCODE_PARTITIONING,
					partitioner);
		}
		return document;
	}
}
