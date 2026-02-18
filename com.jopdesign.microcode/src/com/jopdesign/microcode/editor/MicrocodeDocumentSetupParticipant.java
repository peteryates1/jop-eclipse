package com.jopdesign.microcode.editor;

import org.eclipse.core.filebuffers.IDocumentSetupParticipant;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension3;
import org.eclipse.jface.text.IDocumentPartitioner;
import org.eclipse.jface.text.rules.FastPartitioner;

import com.jopdesign.microcode.syntax.MicrocodePartitionScanner;

/**
 * Sets up document partitioning when a microcode file is opened
 * by any editor (not just ours), enabling syntax-aware operations.
 */
public class MicrocodeDocumentSetupParticipant implements IDocumentSetupParticipant {

	@Override
	public void setup(IDocument document) {
		if (document instanceof IDocumentExtension3 ext3) {
			IDocumentPartitioner partitioner = new FastPartitioner(
					new MicrocodePartitionScanner(),
					MicrocodePartitionScanner.PARTITION_TYPES);
			partitioner.connect(document);
			ext3.setDocumentPartitioner(
					MicrocodePartitionScanner.MICROCODE_PARTITIONING,
					partitioner);
		}
	}
}
