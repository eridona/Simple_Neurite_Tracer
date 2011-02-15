/* -*- mode: java; c-basic-offset: 8; indent-tabs-mode: t; tab-width: 8 -*- */

/* Copyright 2006, 2007, 2008, 2009, 2010 Mark Longair */

/*
  This file is part of the ImageJ plugin "Simple Neurite Tracer".

  The ImageJ plugin "Simple Neurite Tracer" is free software; you
  can redistribute it and/or modify it under the terms of the GNU
  General Public License as published by the Free Software
  Foundation; either version 3 of the License, or (at your option)
  any later version.

  The ImageJ plugin "Simple Neurite Tracer" is distributed in the
  hope that it will be useful, but WITHOUT ANY WARRANTY; without
  even the implied warranty of MERCHANTABILITY or FITNESS FOR A
  PARTICULAR PURPOSE.  See the GNU General Public License for more
  details.

  In addition, as a special exception, the copyright holders give
  you permission to combine this program with free software programs or
  libraries that are released under the Apache Public License.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package tracing;

import ij.*;
import ij.io.*;
import ij.gui.YesNoCancelDialog;

import java.awt.*;
import java.awt.event.*;
import java.io.*;

import java.util.HashSet;

import features.SigmaPalette;
import ij.gui.GenericDialog;

import java.text.DecimalFormat;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.border.EmptyBorder;

import ij.measure.Calibration;

import Skeletonize3D_.Skeletonize3D_;
import skeleton_analysis. AnalyzeSkeleton_;

@SuppressWarnings("serial")
public class NeuriteTracerResultsDialog
	extends JDialog
	implements ActionListener, WindowListener, ItemListener, PathAndFillListener, TextListener, SigmaPalette.SigmaPaletteListener, ImageListener {

	static final boolean verbose = SimpleNeuriteTracer.verbose;

	public PathWindow pw;
	public FillWindow fw;

	protected JMenuBar menuBar;
	protected JMenu fileMenu;
	protected JMenu analysisMenu;

	protected JMenuItem loadMenuItem;
	protected JMenuItem loadLabelsMenuItem;
	protected JMenuItem saveMenuItem;
	protected JMenuItem exportCSVMenuItem;
	protected JMenuItem quitMenuItem;

	protected JMenuItem analyzeSkeletonMenuItem;
	protected JMenuItem makeLineStackMenuItem;
	protected JMenuItem exportCSVMenuItemAgain;
	protected JMenuItem shollAnalysiHelpMenuItem;

	// These are the states that the UI can be in:

	static final int WAITING_TO_START_PATH    = 0;
	static final int PARTIAL_PATH             = 1;
	static final int SEARCHING                = 2;
	static final int QUERY_KEEP               = 3;
	static final int LOGGING_POINTS           = 4;
	static final int DISPLAY_EVS              = 5;
	static final int FILLING_PATHS            = 6;
	static final int CALCULATING_GAUSSIAN     = 7;
	static final int WAITING_FOR_SIGMA_POINT  = 8;
	static final int WAITING_FOR_SIGMA_CHOICE = 9;
	static final int SAVING                   = 10;
	static final int LOADING                  = 11;

	static final String [] stateNames = { "WAITING_TO_START_PATH",
					      "PARTIAL_PATH",
					      "SEARCHING",
					      "QUERY_KEEP",
					      "LOGGING_POINTS",
					      "DISPLAY_EVS",
					      "FILLING_PATHS",
					      "CALCULATING_GAUSSIAN",
					      "WAITING_FOR_SIGMA_POINT",
					      "WAITING_FOR_SIGMA_CHOICE",
					      "SAVING",
					      "LOADING" };

	static final String SEARCHING_STRING = "Searching for path between points...";

	private int currentState;

	SimpleNeuriteTracer plugin;

	JPanel statusPanel;
	JLabel statusText;
	JButton keepSegment, junkSegment;
	JButton cancelSearch;

	JPanel pathActionPanel;
	JButton completePath;
	JButton cancelPath;

	JComboBox viewPathChoice;
	String projectionChoice = "projected through all slices";
	String partsNearbyChoice = "parts in nearby slices";

	TextField nearbyField;

	PathColorsCanvas pathColorsCanvas;

	JComboBox colorImageChoice;
	String noColorImageString = "[None]";
	ImagePlus currentColorImage;

	JCheckBox justShowSelected;

	JComboBox paths3DChoice;
	String [] paths3DChoicesStrings = {
		"BUG",
		"as surface reconstructions",
		"as lines",
		"as lines and discs" };

	JCheckBox preprocess;
	JCheckBox usePreprocessed;

	double currentSigma;
	double currentMultiplier;

	JLabel currentSigmaAndMultiplierLabel;

	JButton editSigma;
	JButton sigmaWizard;

	JButton showCorrespondencesToButton;

	JButton uploadButton;
	JButton fetchButton;

	JButton showOrHidePathList;
	JButton showOrHideFillList;

	// ------------------------------------------------------------------------
	// Implementing the ImageListener interface:

	public void imageOpened(ImagePlus imp) {
		updateColorImageChoice();
	}

	// Called when an image is closed
	public void imageClosed(ImagePlus imp) {
		updateColorImageChoice();
	}

	// Called when an image's pixel data is updated
	public void imageUpdated(ImagePlus imp) {
		updateColorImageChoice();
	}

	// ------------------------------------------------------------------------

	public void updateStatusText(String newStatus) {
		statusText.setText("<html><strong>"+newStatus+"</strong></html>");
	}

	synchronized public void updateColorImageChoice() {

		// Try to preserve the old selection:
		String oldSelection = (String) colorImageChoice.getSelectedItem();

		colorImageChoice.removeAllItems();

		int j = 0;
		colorImageChoice.addItem(noColorImageString);

		int selectedIndex = 0;

		int[] wList = WindowManager.getIDList();
		if (wList!=null) {
			for (int i=0; i<wList.length; i++) {
				ImagePlus imp = WindowManager.getImage(wList[i]);
				j++;
				String title = imp.getTitle();
				colorImageChoice.addItem(title);
				if (title == oldSelection)
					selectedIndex = j;
			}
		}

		colorImageChoice.setSelectedIndex(selectedIndex);
		// This doesn't trigger an item event
		checkForColorImageChange();
	}

	public static boolean similarCalibrations(Calibration a, Calibration b) {
		double ax = 1, ay = 1, az = 1;
		double bx = 1, by = 1, bz = 1;
		if( a != null ) {
			ax = a.pixelWidth;
			ay = a.pixelHeight;
			az = a.pixelDepth;
		}
		if( b != null ) {
			bx = b.pixelWidth;
			by = b.pixelHeight;
			bz = b.pixelDepth;
		}
		double pixelWidthDifference = Math.abs( ax - bx );
		double pixelHeightDifference = Math.abs( ay - by );
		double pixelDepthDifference = Math.abs( az - bz );
		double epsilon = 0.000001;
		if( pixelWidthDifference > epsilon )
			return false;
		if( pixelHeightDifference > epsilon )
			return false;
		if( pixelDepthDifference > epsilon )
			return false;
		return true;
	}

	synchronized public void checkForColorImageChange() {
		String selectedTitle = (String) colorImageChoice.getSelectedItem();

		ImagePlus intendedColorImage = null;
		if( selectedTitle != null && ! selectedTitle.equals(noColorImageString) ) {
			intendedColorImage = WindowManager.getImage(selectedTitle);
		}

		if( intendedColorImage != currentColorImage ) {
			if( intendedColorImage != null ) {
				ImagePlus image = plugin.getImagePlus();
				Calibration calibration = plugin.getImagePlus().getCalibration();
				Calibration colorImageCalibration = intendedColorImage.getCalibration();
				if( ! similarCalibrations( calibration,
							   colorImageCalibration ) ) {
					IJ.error("Warning: the calibration of '"+intendedColorImage.getTitle()+"' is different from the image you're tracing ('"+image.getTitle()+"')'\nThis may produce unexpected results.");
				}
				if( ! (intendedColorImage.getWidth() == image.getWidth() &&
				       intendedColorImage.getHeight() == image.getHeight() &&
				       intendedColorImage.getStackSize() == image.getStackSize()) )
					IJ.error("Warning: the dimensions (in voxels) of '"+intendedColorImage.getTitle()+"' is different from the image you're tracing ('"+image.getTitle()+"')'\nThis may produce unexpected results.");
			}
			currentColorImage = intendedColorImage;
			plugin.setColorImage(currentColorImage);
		}
	}

	public void newSigmaSelected( double sigma ) {
		setSigma( sigma, false );
	}

	public void newMaximum( double max ) {
		double multiplier = 256 / max;
		setMultiplier( multiplier );
	}

	// ------------------------------------------------------------------------
	// FIXME: consider moving these into SimpleNeuriteTracer

	@Override
	public void setPathList( String [] newList, Path justAdded, boolean expandAll ) { }

	@Override
	public void setFillList( String [] newList ) { }

	// Note that rather unexpectedly the p.setSelcted calls make sure that
	// the colour of the path in the 3D viewer is right...  (FIXME)
	@Override
	public void setSelectedPaths( HashSet<Path> selectedPathsSet, Object source ) {
		if( source == this )
			return;
		for( int i = 0; i < pathAndFillManager.size(); ++i ) {
			Path p = pathAndFillManager.getPath(i);
			if( selectedPathsSet.contains(p) ) {
				p.setSelected( true );
			} else {
				p.setSelected( false );
			}
		}
	}

	// ------------------------------------------------------------------------

	int preGaussianState;
	int preSigmaPaletteState;

	public void gaussianCalculated(boolean succeeded) {
		if( !succeeded )
			preprocess.setSelected(false);
		changeState(preGaussianState);
		if( preprocess.isSelected() ) {
			editSigma.setEnabled(false);
			sigmaWizard.setEnabled(false);
		} else {
			editSigma.setEnabled(true);
			sigmaWizard.setEnabled(true);
		}
	}

	public void setMultiplier( double multiplier ) {
		currentMultiplier = multiplier;
		updateLabel( );
	}

	public void setSigma( double sigma, boolean mayStartGaussian ) {
		currentSigma = sigma;
		updateLabel( );
		if( mayStartGaussian ) {
			if( preprocess.isSelected() ) {
				IJ.error( "[BUG] The preprocess checkbox should never be on when setSigma is called" );
			} else {
				// Turn on the checkbox:
				preprocess.setSelected( true );
				/* ... according to the documentation
				   this doesn't generate an event, so
				   we manually turn on the Gaussian
				   calculation */
				turnOnHessian();
			}
		}
	}

	public void turnOnHessian( ) {
		preGaussianState = currentState;
		plugin.enableHessian(true);
	}

	DecimalFormat threeDecimalPlaces = new DecimalFormat("0.0000");
	DecimalFormat threeDecimalPlacesScientific = new DecimalFormat("0.00E00");

	public String formatDouble( double value ) {
		double absValue = Math.abs( value );
		if( absValue < 0.01 || absValue >= 1000 )
			return threeDecimalPlacesScientific.format(value);
		else
			return threeDecimalPlaces.format(value);
	}

	public void updateLabel( ) {
		currentSigmaAndMultiplierLabel.setText(
			"\u03C3 = " +
			formatDouble( currentSigma ) +
			", multiplier = " + formatDouble( currentMultiplier ) );
	}

	public double getSigma( ) {
		return currentSigma;
	}

	public double getMultiplier( ) {
		return currentMultiplier;
	}

	public void exitRequested() {

		// FIXME: check that everything is saved...

		if( plugin.pathsUnsaved() ) {

			YesNoCancelDialog d = new YesNoCancelDialog( IJ.getInstance(), "Really quit?",
								     "There are unsaved paths. Do you really want to quit?" );

			if( ! d.yesPressed() )
				return;

		}

		plugin.cancelSearch( true );
		pw.dispose();
		fw.dispose();
		dispose();
		plugin.closeAndReset();
	}

	public void disableEverything() {

		fw.setEnabledNone();
		pw.setButtonsEnabled(false);

		statusText.setEnabled(false);
		keepSegment.setEnabled(false);
		junkSegment.setEnabled(false);
		cancelSearch.setEnabled(false);
		completePath.setEnabled(false);
		cancelPath.setEnabled(false);

		editSigma.setEnabled(false);
		sigmaWizard.setEnabled(false);

		viewPathChoice.setEnabled(false);
		paths3DChoice.setEnabled(false);
		preprocess.setEnabled(false);

		exportCSVMenuItem.setEnabled(false);
		exportCSVMenuItemAgain.setEnabled(false);
		analyzeSkeletonMenuItem.setEnabled(false);
		saveMenuItem.setEnabled(false);
		loadMenuItem.setEnabled(false);
		if( uploadButton != null ) {
			uploadButton.setEnabled(false);
			fetchButton.setEnabled(false);
		}
		loadLabelsMenuItem.setEnabled(false);

		quitMenuItem.setEnabled(false);
	}

	public void changeState( int newState ) {

		if (verbose) System.out.println("changeState to: "+stateNames[newState]);

		switch( newState ) {

		case WAITING_TO_START_PATH:
			updateStatusText("Click somewhere to start a new path...");
			disableEverything();
			pw.setButtonsEnabled(true);
			// Fake a selection change in the path tree:
			pw.valueChanged( null );

			cancelSearch.setVisible(false);
			keepSegment.setVisible(false);
			junkSegment.setVisible(false);

			viewPathChoice.setEnabled(true);
			paths3DChoice.setEnabled(true);
			preprocess.setEnabled(true);

			editSigma.setEnabled( ! preprocess.isSelected() );
			sigmaWizard.setEnabled( ! preprocess.isSelected() );

			fw.setEnabledWhileNotFilling();

			loadLabelsMenuItem.setEnabled(true);

			saveMenuItem.setEnabled(true);
			loadMenuItem.setEnabled(true);
			exportCSVMenuItem.setEnabled(true);
			exportCSVMenuItemAgain.setEnabled(true);
			analyzeSkeletonMenuItem.setEnabled(true);
			if( uploadButton != null ) {
				uploadButton.setEnabled(true);
				fetchButton.setEnabled(true);
			}

			quitMenuItem.setEnabled(true);

			break;

		case PARTIAL_PATH:
			updateStatusText("Now select a point further along that structure...");
			disableEverything();

			cancelSearch.setVisible(false);
			keepSegment.setVisible(false);
			junkSegment.setVisible(false);

			if( plugin.justFirstPoint() )
				completePath.setEnabled(false);
			else
				completePath.setEnabled(true);
			cancelPath.setEnabled(true);

			viewPathChoice.setEnabled(true);
			paths3DChoice.setEnabled(true);
			preprocess.setEnabled(true);

			editSigma.setEnabled( ! preprocess.isSelected() );
			sigmaWizard.setEnabled( ! preprocess.isSelected() );

			quitMenuItem.setEnabled(false);

			break;

		case SEARCHING:
			updateStatusText("Searching for path between points...");
			disableEverything();

			cancelSearch.setText("Abandon search");
			cancelSearch.setEnabled(true);
			cancelSearch.setVisible(true);
			keepSegment.setVisible(false);
			junkSegment.setVisible(false);

			completePath.setEnabled(false);
			cancelPath.setEnabled(false);

			quitMenuItem.setEnabled(true);

			break;

		case QUERY_KEEP:
			updateStatusText("Keep this new path segment?");
			disableEverything();

			keepSegment.setEnabled(true);
			junkSegment.setEnabled(true);

			cancelSearch.setVisible(false);
			keepSegment.setVisible(true);
			junkSegment.setVisible(true);

			break;

		case FILLING_PATHS:
			updateStatusText("Filling out from neuron...");
			disableEverything();

			fw.setEnabledWhileFilling();

			break;

		case CALCULATING_GAUSSIAN:
			updateStatusText("Calculating Gaussian...");
			disableEverything();

			cancelSearch.setText("Cancel");
			cancelSearch.setEnabled(true);
			cancelSearch.setVisible(true);
			keepSegment.setVisible(true);
			junkSegment.setVisible(true);

			break;

		case WAITING_FOR_SIGMA_POINT:
			updateStatusText("Click on a neuron in the image");
			disableEverything();
			break;

		case WAITING_FOR_SIGMA_CHOICE:
			updateStatusText("Close the sigma palette window to continue");
			disableEverything();
			break;

		case LOADING:
			updateStatusText("Loading...");
			disableEverything();
			break;

		case SAVING:
			updateStatusText("Saving...");
			disableEverything();
			break;

		default:
			IJ.error("BUG: switching to an unknown state");
			return;
		}

		pack();

		plugin.repaintAllPanes();

		currentState = newState;

	}

	public int getState() {
		return currentState;
	}

	// ------------------------------------------------------------------------

	public void windowClosing( WindowEvent e ) {
		exitRequested();
	}

	public void windowActivated( WindowEvent e ) { }
	public void windowDeactivated( WindowEvent e ) { }
	public void windowClosed( WindowEvent e ) { }
	public void windowOpened( WindowEvent e ) { }
	public void windowIconified( WindowEvent e ) { }
	public void windowDeiconified( WindowEvent e ) { }

	public void updateSearchingStatistics( int pointsInOpenBoundary ) {
		updateStatusText( SEARCHING_STRING + " ("+pointsInOpenBoundary+" boundary points.)" );
	}

	private PathAndFillManager pathAndFillManager;

	boolean launchedByArchive;

	public NeuriteTracerResultsDialog( String title,
					   SimpleNeuriteTracer plugin,
					   boolean launchedByArchive ) {

		super( IJ.getInstance(), title, false );
		this.plugin = plugin;
		this.launchedByArchive = launchedByArchive;

		pathAndFillManager = plugin.getPathAndFillManager();

		// Create the menu bar and menus:

		menuBar = new JMenuBar();

		fileMenu = new JMenu("File");
		menuBar.add(fileMenu);

		analysisMenu = new JMenu("Analysis");
		menuBar.add(analysisMenu);

		loadMenuItem = new JMenuItem("Load traces / SWC file...");
		loadMenuItem.addActionListener(this);
		fileMenu.add(loadMenuItem);

		loadLabelsMenuItem = new JMenuItem("Load labels file...");
		loadLabelsMenuItem.addActionListener(this);
		fileMenu.add(loadLabelsMenuItem);

		saveMenuItem = new JMenuItem("Save traces file...");
		saveMenuItem.addActionListener(this);
		fileMenu.add(saveMenuItem);

		exportCSVMenuItem = new JMenuItem("Export as CSV...");
		exportCSVMenuItem.addActionListener(this);
		fileMenu.add(exportCSVMenuItem);

		quitMenuItem = new JMenuItem("Quit");
		quitMenuItem.addActionListener(this);
		fileMenu.add(quitMenuItem);

		analyzeSkeletonMenuItem = new JMenuItem("Run \"Analyze Skeleton\"");
		analyzeSkeletonMenuItem.addActionListener(this);
		analysisMenu.add(analyzeSkeletonMenuItem);

		makeLineStackMenuItem = new JMenuItem("Make Line Stack");
		makeLineStackMenuItem.addActionListener(this);
		analysisMenu.add(makeLineStackMenuItem);

		exportCSVMenuItemAgain = new JMenuItem("Export as CSV...");
		exportCSVMenuItemAgain.addActionListener(this);
		analysisMenu.add(exportCSVMenuItemAgain);

		shollAnalysiHelpMenuItem = new JMenuItem("Sholl Analysis help...");
		shollAnalysiHelpMenuItem.addActionListener(this);
		analysisMenu.add(shollAnalysiHelpMenuItem);

		setJMenuBar(menuBar);

		addWindowListener(this);

		getContentPane().setLayout(new GridBagLayout());

		GridBagConstraints c = new GridBagConstraints();

		c.anchor = GridBagConstraints.LINE_START;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.insets = new Insets( 10, 10, 4, 10 );
		c.gridy = 0;
		c.weightx = 1;

		{ /* Add the status panel */

			statusPanel = new JPanel();
			statusPanel.setLayout(new BorderLayout());
			statusPanel.add(new JLabel("Instructions:"), BorderLayout.NORTH);
			statusText = new JLabel("");
			statusText.setOpaque(true);
			statusText.setForeground(Color.black);
			statusText.setBackground(Color.white);
			updateStatusText("Initial status text");
			statusText.setBorder( new EmptyBorder( 5, 5, 5, 5 ) );
			statusPanel.add(statusText,BorderLayout.CENTER);

			keepSegment = new JButton("Yes");
			junkSegment = new JButton("No");
			cancelSearch = new JButton("Abandon Search");

			keepSegment.addActionListener( this );
			junkSegment.addActionListener( this );
			cancelSearch.addActionListener( this );

			JPanel statusChoicesPanel = new JPanel();
			/*
			statusChoicesPanel.setLayout( new GridBagLayout() );
			GridBagConstraints cs = new GridBagConstraints();
			cs.weightx = 1;
			cs.gridx = 0; cs.gridy = 0; cs.anchor = GridBagConstraints.LINE_START;
			statusChoicesPanel.add(keepSegment,cs);
			cs.gridx = 1; cs.gridy = 0; cs.anchor = GridBagConstraints.LINE_START;
			statusChoicesPanel.add(junkSegment,cs);
			cs.gridx = 2; cs.gridy = 0; cs.anchor = GridBagConstraints.LINE_START;
			statusChoicesPanel.add(cancelSearch,cs);
			*/
			statusChoicesPanel.add(keepSegment);
			statusChoicesPanel.add(junkSegment);
			statusChoicesPanel.add(cancelSearch);
			statusChoicesPanel.setLayout(new FlowLayout());

			statusPanel.add(statusChoicesPanel,BorderLayout.SOUTH);

			getContentPane().add(statusPanel,c);
		}

		c.insets = new Insets( 4, 10, 10, 10 );

		{ /* Add the panel of actions to take on half-constructed paths */

			pathActionPanel = new JPanel();
			completePath = new JButton("Complete Path");
			cancelPath = new JButton("Cancel Path");
			completePath.addActionListener( this );
			cancelPath.addActionListener( this );
			pathActionPanel.add(completePath);
			pathActionPanel.add(cancelPath);

			++ c.gridy;
			getContentPane().add(pathActionPanel,c);
		}

		c.insets = new Insets( 10, 10, 10, 10 );

		{
			JPanel viewOptionsPanel = new JPanel();

			viewOptionsPanel.setLayout(new GridBagLayout());
			GridBagConstraints cv = new GridBagConstraints();
			cv.insets = new Insets(3, 2, 3, 2);
			cv.anchor = GridBagConstraints.LINE_START;
			viewPathChoice = new JComboBox();
			viewPathChoice.addItem(projectionChoice);
			viewPathChoice.addItem(partsNearbyChoice);
			viewPathChoice.addItemListener(this);

			JPanel nearbyPanel = new JPanel();
			nearbyPanel.setLayout(new BorderLayout());
			nearbyPanel.add(new JLabel("(up to"),BorderLayout.WEST);
			nearbyField = new TextField("2",2);
			nearbyField.addTextListener(this);
			nearbyPanel.add(nearbyField,BorderLayout.CENTER);
			nearbyPanel.add(new JLabel("slices to each side)"),BorderLayout.EAST);

			cv.gridx = 0;
			cv.gridy = 0;
			viewOptionsPanel.add(new JLabel("View paths (2D): "),cv);
			cv.gridx = 1;
			cv.gridy = 0;
			viewOptionsPanel.add(viewPathChoice,cv);

			paths3DChoice = new JComboBox();
			if( plugin != null && plugin.use3DViewer ) {
				for( int choice = 1; choice < paths3DChoicesStrings.length; ++choice )
					paths3DChoice.addItem(paths3DChoicesStrings[choice]);

				cv.gridx = 0;
				++ cv.gridy;
				viewOptionsPanel.add(new JLabel("View paths (3D): "),cv);
				cv.gridx = 1;
				viewOptionsPanel.add(paths3DChoice,cv);
			}
			paths3DChoice.addItemListener(this);

			cv.gridx = 1;
			++ cv.gridy;
			cv.gridwidth = 1;
			cv.anchor = GridBagConstraints.LINE_START;
			viewOptionsPanel.add(nearbyPanel, cv);

			JPanel flatColorOptionsPanel = new JPanel();
			flatColorOptionsPanel.setLayout(new BorderLayout());
			flatColorOptionsPanel.add(new JLabel("Click to change Path colours:"), BorderLayout.NORTH);
			pathColorsCanvas = new PathColorsCanvas(plugin, 150, 18);
			flatColorOptionsPanel.add(pathColorsCanvas, BorderLayout.CENTER);

			JPanel imageColorOptionsPanel = new JPanel();
			imageColorOptionsPanel.setLayout(new BorderLayout());
			imageColorOptionsPanel.add(new JLabel("Use colors / labels from:"), BorderLayout.NORTH);

			colorImageChoice = new JComboBox();
			updateColorImageChoice();
			colorImageChoice.addItemListener(this);
			imageColorOptionsPanel.add(colorImageChoice, BorderLayout.CENTER);
			ImagePlus.addImageListener(this);

			cv.gridx = 0;
			++cv.gridy;
			cv.gridwidth = 2;
			viewOptionsPanel.add(flatColorOptionsPanel,cv);

			cv.gridx = 0;
			++ cv.gridy;
			cv.gridwidth = 2;
			viewOptionsPanel.add(imageColorOptionsPanel,cv);

			justShowSelected = new JCheckBox( "Show only selected paths" );
			justShowSelected.addItemListener( this );
			cv.gridx = 0;
			++ cv.gridy;
			cv.gridwidth = 2;
			cv.anchor = GridBagConstraints.LINE_START;
			cv.insets = new Insets( 0, 0, 0, 0 );
			viewOptionsPanel.add(justShowSelected,cv);

			++ c.gridy;
			getContentPane().add(viewOptionsPanel,c);
		}

		{ /* Add the panel with other options - preprocessing and the view of paths */

			JPanel otherOptionsPanel = new JPanel();

			otherOptionsPanel.setLayout(new GridBagLayout());
			GridBagConstraints co = new GridBagConstraints();
			co.anchor = GridBagConstraints.LINE_START;

			preprocess = new JCheckBox("Hessian-based analysis");
			preprocess.addItemListener( this );

			co.gridx = 0;
			++ co.gridy;
			co.gridwidth = 2;
			co.anchor = GridBagConstraints.LINE_START;
			otherOptionsPanel.add(preprocess,co);

			++ co.gridy;
			usePreprocessed = new JCheckBox("Use preprocessed image");
			usePreprocessed.addItemListener( this );
			usePreprocessed.setEnabled( plugin.tubeness != null );
			otherOptionsPanel.add(usePreprocessed,co);

			co.fill = GridBagConstraints.HORIZONTAL;

			currentSigmaAndMultiplierLabel = new JLabel();
			++ co.gridy;
			otherOptionsPanel.add(currentSigmaAndMultiplierLabel,co);
			setSigma( plugin.getMinimumSeparation(), false );
			setMultiplier( 4 );
			updateLabel( );
			++ co.gridy;

			JPanel sigmaButtonPanel = new JPanel( );

			editSigma = new JButton( "Pick Sigma Manually" );
			editSigma.addActionListener( this );
			sigmaButtonPanel.add(editSigma);

			sigmaWizard = new JButton( "Pick Sigma Visually" );
			sigmaWizard.addActionListener( this );
			sigmaButtonPanel.add(sigmaWizard);

			++ co.gridy;
			otherOptionsPanel.add(sigmaButtonPanel,co);

			++ c.gridy;
			getContentPane().add(otherOptionsPanel,c);
		}

		{
			JPanel hideWindowsPanel = new JPanel();
			showOrHidePathList = new JButton("Show / Hide Path List");
			showOrHidePathList.addActionListener(this);
			showOrHideFillList = new JButton("Show / Hide Fill List");
			showOrHideFillList.addActionListener(this);
			hideWindowsPanel.add( showOrHidePathList );
			hideWindowsPanel.add( showOrHideFillList );
			c.fill = GridBagConstraints.HORIZONTAL;
			++ c.gridy;
			getContentPane().add( hideWindowsPanel, c );
		}

		pack();

		pw = new PathWindow(
			pathAndFillManager,
			plugin,
			getX() + getWidth(),
			getY() );

		fw = new FillWindow(
			pathAndFillManager,
			plugin,
			getX() + getWidth(),
			getY() + pw.getHeight() );

		changeState( WAITING_TO_START_PATH );
	}

	public void displayOnStarting( ) {
		setVisible( true );
		setPathListVisible( true );
		setFillListVisible( false );
	}

	public void showMouseThreshold( float t ) {
		String newStatus = null;
		if( t < 0 ) {
			newStatus = "Not reached by search yet";
		} else {
			newStatus = "Distance from path is: " + t;
		}
		fw.fillStatus.setText( newStatus );
	}

	public void actionPerformed( ActionEvent e ) {

		Object source = e.getSource();

		/* if( source == uploadButton ) {
			plugin.uploadTracings();
		} else if( source == fetchButton ) {
			plugin.getTracings( true );
		} else */ if( source == saveMenuItem ) {

			FileInfo info = plugin.file_info;
			SaveDialog sd;

			if( info == null ) {

				sd = new SaveDialog("Save traces as...",
						    "image",
						    ".traces");

			} else {

				String fileName = info.fileName;
				String directory = info.directory;

				String suggestedSaveFilename;

				suggestedSaveFilename = fileName;

				sd = new SaveDialog("Save traces as...",
						    directory,
						    suggestedSaveFilename,
						    ".traces");
			}

			String savePath;
			if(sd.getFileName()==null) {
				return;
			} else {
				savePath = sd.getDirectory()+sd.getFileName();
			}

			File file = new File(savePath);
			if ((file!=null)&&file.exists()) {
				if (!IJ.showMessageWithCancel(
					    "Save traces file...", "The file "+
					    savePath+" already exists.\n"+
					    "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Saving traces to "+savePath);

			int preSavingState = currentState;
			changeState( SAVING );
			try {
				pathAndFillManager.writeXML( savePath, true );
			} catch( IOException ioe ) {
				IJ.showStatus("Saving failed.");
				IJ.error("Writing traces to '"+savePath+"' failed: "+ioe);
				changeState( preSavingState );
				return;
			}
			changeState( preSavingState );
			IJ.showStatus("Saving completed.");

			plugin.unsavedPaths = false;

		} else if( source == loadMenuItem ) {

			if( plugin.pathsUnsaved() ) {
				YesNoCancelDialog d = new YesNoCancelDialog( IJ.getInstance(), "Warning",
									     "There are unsaved paths. Do you really want to load new traces?" );

				if( ! d.yesPressed() )
					return;
			}

			int preLoadingState = currentState;
			changeState( LOADING );
			plugin.loadTracings();
			changeState( preLoadingState );

		} else if( source == exportCSVMenuItem || source == exportCSVMenuItemAgain ) {

			FileInfo info = plugin.file_info;
			SaveDialog sd;

			if( info == null ) {

				sd = new SaveDialog("Export as CSV...",
						    "traces",
						    ".csv");

			} else {

				sd = new SaveDialog("Export as CSV...",
						    info.directory,
						    info.fileName,
						    ".csv");
			}

			String savePath;
			if(sd.getFileName()==null) {
				return;
			} else {
				savePath = sd.getDirectory()+sd.getFileName();
			}

			File file = new File(savePath);
			if ((file!=null)&&file.exists()) {
				if (!IJ.showMessageWithCancel(
					    "Export as CSV...", "The file "+
					    savePath+" already exists.\n"+
					    "Do you want to replace it?"))
					return;
			}

			IJ.showStatus("Exporting as CSV to "+savePath);

			int preExportingState = currentState;
			changeState( SAVING );
			// Export here...
			try {
				pathAndFillManager.exportToCSV(file);
			} catch( IOException ioe ) {
				IJ.showStatus("Exporting failed.");
				IJ.error("Writing traces to '"+savePath+"' failed: "+ioe);
				changeState( preExportingState );
				return;
			}
			IJ.showStatus("Export complete.");
			changeState( preExportingState );

		} else if( source == showCorrespondencesToButton ) {

			// Ask for the traces file to show correspondences to:

			String fileName = null;
			String directory = null;

			OpenDialog od;
			od = new OpenDialog("Select other traces file...",
					    directory,
					    null );

			fileName = od.getFileName();
			directory = od.getDirectory();

			if( fileName != null ) {

				File tracesFile = new File( directory, fileName );
				if( ! tracesFile.exists() ) {
					IJ.error("The file '"+tracesFile.getAbsolutePath()+"' does not exist.");
					return;
				}

				/* FIXME: test code: */

				// File tracesFile = new File("/media/LaCie/corpus/flybrain/Data/1/lo15r202.fitted.traces");
				// File fittedTracesFile = new File("/media/LaCie/corpus/flybrain/Data/1/LO15R202.traces");

				// plugin.showCorrespondencesTo( tracesFile, Color.YELLOW, 2.5 );
				// plugin.showCorrespondencesTo( fittedTracesFile, Color.RED, 2.5 );

				plugin.showCorrespondencesTo( tracesFile, Color.YELLOW, 2.5 );

				/* end of FIXME */

			}


		} else if( source == loadLabelsMenuItem ) {

			plugin.loadLabels();

		} else if( source == makeLineStackMenuItem ) {

			if( pathAndFillManager.size() == 0 ) {
				IJ.error("There are no paths traced yet - the stack would be empty");
			} else {
				ImagePlus imagePlus = plugin.makePathVolume();
				imagePlus.show();
			}

		} else if( source == analyzeSkeletonMenuItem ) {

			if( pathAndFillManager.size() == 0 ) {
				IJ.error("There are no paths traced yet!");
			} else {
				ImagePlus imagePlus = plugin.makePathVolume();
				Skeletonize3D_ skeletonizer = new Skeletonize3D_();
				skeletonizer.setup("",imagePlus);
				skeletonizer.run(imagePlus.getProcessor());
				AnalyzeSkeleton_ analyzer = new AnalyzeSkeleton_();
				analyzer.setup("",imagePlus);
				analyzer.run(imagePlus.getProcessor());
				imagePlus.show();
			}

		} else if( source == shollAnalysiHelpMenuItem ) {

			IJ.runPlugIn("ij.plugin.BrowserLauncher", "http://pacific.mpi-cbg.de/wiki/index.php/Simple_Neurite_Tracer:_Sholl_analysis");

		} else if( source == cancelSearch ) {

			if( currentState == SEARCHING ) {
				updateStatusText("Cancelling path search...");
				plugin.cancelSearch( false );
			} else if( currentState == CALCULATING_GAUSSIAN ) {
				updateStatusText("Cancelling Gaussian generation...");
				plugin.cancelGaussian();
			} else {
				IJ.error("BUG! (wrong state for cancelling...)");
			}

		} else if( source == keepSegment ) {

			plugin.confirmTemporary( );

		} else if( source == junkSegment ) {

			plugin.cancelTemporary( );

		} else if( source == completePath ) {

			plugin.finishedPath( );

		} else if( source == cancelPath ) {

			plugin.cancelPath( );

		} else if( source == quitMenuItem ) {

			exitRequested();

		}  else if( source == showOrHidePathList ) {

			togglePathListVisibility();

		}  else if( source == showOrHideFillList ) {

			toggleFillListVisibility();

		} else if( source == editSigma ) {

			double newSigma = -1;
			double newMultiplier = -1;
			while( newSigma <= 0 ) {
				GenericDialog gd = new GenericDialog("Select Scale of Structures");
				gd.addMessage("Please enter the approximate radius of the structures you are looking for:");
				gd.addNumericField("Sigma: ", plugin.getMinimumSeparation(), 4);
				gd.addMessage("(The default value is the minimum voxel separation.)");
				gd.addMessage("Please enter the scaling factor to apply:");
				gd.addNumericField("Multiplier: ", 4, 4);
				gd.addMessage("(If you're not sure, just leave this at 4.)");
				gd.showDialog();
				if( gd.wasCanceled() )
					return;

				newSigma = gd.getNextNumber();
				if( newSigma <= 0 ) {
					IJ.error("The value of sigma must be positive");
				}

				newMultiplier = gd.getNextNumber();
				if( newMultiplier <= 0 ) {
					IJ.error("The value of the multiplier must be positive");
				}
			}

			setSigma( newSigma, true );
			setMultiplier( newMultiplier );

		} else if( source == sigmaWizard ) {

			preSigmaPaletteState = currentState;
			changeState( WAITING_FOR_SIGMA_POINT );
		}
	}

	public void sigmaPaletteClosing() {
		changeState(preSigmaPaletteState);
		setSigma( currentSigma, true );
	}

	public void setPathListVisible(boolean makeVisible) {
		if( makeVisible ) {
			showOrHidePathList.setText("Hide Path List");
			pw.setVisible(true);
			pw.toFront();
		} else {
			showOrHidePathList.setText("Show Path List");
			pw.setVisible(false);
		}
	}

	public void togglePathListVisibility() {
		synchronized (pw) {
			setPathListVisible( ! pw.isVisible() );
		}
	}

	public void setFillListVisible(boolean makeVisible) {
		if( makeVisible ) {
			showOrHideFillList.setText("Hide Fill List");
			fw.setVisible(true);
			fw.toFront();
		} else {
			showOrHideFillList.setText("Show Fill List");
			fw.setVisible(false);
		}
	}

	public void toggleFillListVisibility() {
		synchronized (fw) {
			setFillListVisible( ! fw.isVisible() );
		}
	}

	public void thresholdChanged( double f ) {
		fw.thresholdChanged(f);
	}

	public boolean nearbySlices( ) {
		return ( viewPathChoice.getSelectedIndex() > 0 );
	}

	public void itemStateChanged( ItemEvent e ) {

		Object source = e.getSource();

		if( source == viewPathChoice ) {

			plugin.justDisplayNearSlices(nearbySlices(),getEitherSide());

		} else if( source == preprocess ) {

			if( preprocess.isSelected() )
				turnOnHessian();
			else {
				plugin.enableHessian(false);
				// changeState(preGaussianState);
			}

		} else if( source == usePreprocessed ) {

			if( usePreprocessed.isSelected() ) {
				preprocess.setSelected(false);
			}

		}  else if( source == justShowSelected ) {

			plugin.setShowOnlySelectedPaths( justShowSelected.isSelected() );

		} else if( source == paths3DChoice ) {

			int selectedIndex = paths3DChoice.getSelectedIndex();
			plugin.setPaths3DDisplay( selectedIndex + 1 );

		} else if( source == colorImageChoice ) {
			checkForColorImageChange();
		}

	}

	@Override
	public void paint(Graphics g) {
		super.paint(g);
	}

	boolean reportedInvalid;

	public int getEitherSide( ) {

		String s = nearbyField.getText();
		if( s.equals("") ) {
			reportedInvalid = false;
			return 0;
		}

		try {
			int e = Integer.parseInt( s );
			if( e < 0 ) {
				if( ! reportedInvalid ) {
					IJ.error("The number of slices either side cannot be negative.");
					reportedInvalid = true;
					return 0;
				}
			}
			reportedInvalid = false;
			return e;

		} catch( NumberFormatException nfe ) {
			if( ! reportedInvalid ) {
				IJ.error("The number of slices either side must be a non-negative integer.");
				reportedInvalid = true;
				return 0;
			}
			return 0;
		}

	}

	public void textValueChanged( TextEvent e ) {
		plugin.justDisplayNearSlices(nearbySlices(),getEitherSide());
	}


	public void threadStatus( SearchThread source, int threadStatus ) {
		if (verbose) System.out.println("threadStatus reported as: "+threadStatus);
	}

	public void finished( SearchThread source, boolean success ) {
		// Unused
	}

	public void pointsInSearch( SearchThread source, int inOpen, int inClosed ) {
		// Unused
	}
}
