/*
    This file is part of RouteConverter.

    RouteConverter is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation; either version 2 of the License, or
    (at your option) any later version.

    RouteConverter is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with RouteConverter; if not, write to the Free Software
    Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA

    Copyright (C) 2007 Christian Pesch. All Rights Reserved.
*/
package slash.navigation.mapview.mapsforge;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.model.Dimension;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.MapPosition;
import org.mapsforge.map.layer.Layer;
import org.mapsforge.map.layer.LayerManager;
import org.mapsforge.map.layer.Layers;
import org.mapsforge.map.layer.cache.InMemoryTileCache;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.download.TileDownloadLayer;
import org.mapsforge.map.layer.download.tilesource.TileSource;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.model.MapViewPosition;
import org.mapsforge.map.model.common.Observer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.scalebar.DefaultMapScaleBar;
import org.mapsforge.map.scalebar.ImperialUnitAdapter;
import org.mapsforge.map.scalebar.MetricUnitAdapter;
import org.mapsforge.map.scalebar.NauticalUnitAdapter;
import org.mapsforge.map.util.MapViewProjection;
import slash.navigation.base.BaseRoute;
import slash.navigation.base.RouteCharacteristics;
import slash.navigation.common.BoundingBox;
import slash.navigation.common.LongitudeAndLatitude;
import slash.navigation.common.NavigationPosition;
import slash.navigation.common.UnitSystem;
import slash.navigation.converter.gui.models.BooleanModel;
import slash.navigation.converter.gui.models.CharacteristicsModel;
import slash.navigation.converter.gui.models.FixMapModeModel;
import slash.navigation.converter.gui.models.GoogleMapsServerModel;
import slash.navigation.converter.gui.models.PositionColumnValues;
import slash.navigation.converter.gui.models.PositionsModel;
import slash.navigation.converter.gui.models.PositionsSelectionModel;
import slash.navigation.converter.gui.models.UnitSystemModel;
import slash.navigation.gui.Application;
import slash.navigation.gui.actions.ActionManager;
import slash.navigation.gui.actions.FrameAction;
import slash.navigation.maps.LocalMap;
import slash.navigation.maps.MapManager;
import slash.navigation.mapview.MapView;
import slash.navigation.mapview.MapViewCallback;
import slash.navigation.mapview.MapViewListener;
import slash.navigation.mapview.mapsforge.helpers.MapViewCoordinateDisplayer;
import slash.navigation.mapview.mapsforge.helpers.MapViewMoverAndZoomer;
import slash.navigation.mapview.mapsforge.helpers.MapViewPopupMenu;
import slash.navigation.mapview.mapsforge.helpers.MapViewResizer;
import slash.navigation.mapview.mapsforge.lines.Line;
import slash.navigation.mapview.mapsforge.lines.Polyline;
import slash.navigation.mapview.mapsforge.models.IntermediateRoute;
import slash.navigation.mapview.mapsforge.overlays.DraggableMarker;
import slash.navigation.mapview.mapsforge.updater.EventMapUpdater;
import slash.navigation.mapview.mapsforge.updater.PairWithLayer;
import slash.navigation.mapview.mapsforge.updater.PositionWithLayer;
import slash.navigation.mapview.mapsforge.updater.SelectionOperation;
import slash.navigation.mapview.mapsforge.updater.SelectionUpdater;
import slash.navigation.mapview.mapsforge.updater.TrackOperation;
import slash.navigation.mapview.mapsforge.updater.TrackUpdater;
import slash.navigation.mapview.mapsforge.updater.WaypointOperation;
import slash.navigation.mapview.mapsforge.updater.WaypointUpdater;
import slash.navigation.routing.DownloadFuture;
import slash.navigation.routing.RoutingResult;
import slash.navigation.routing.RoutingService;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.KeyEvent.VK_DOWN;
import static java.awt.event.KeyEvent.VK_LEFT;
import static java.awt.event.KeyEvent.VK_MINUS;
import static java.awt.event.KeyEvent.VK_PLUS;
import static java.awt.event.KeyEvent.VK_RIGHT;
import static java.awt.event.KeyEvent.VK_UP;
import static java.lang.Integer.MAX_VALUE;
import static java.lang.Math.max;
import static java.lang.Thread.sleep;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.event.TableModelEvent.ALL_COLUMNS;
import static javax.swing.event.TableModelEvent.DELETE;
import static javax.swing.event.TableModelEvent.INSERT;
import static javax.swing.event.TableModelEvent.UPDATE;
import static org.mapsforge.core.graphics.Color.BLUE;
import static org.mapsforge.core.util.LatLongUtils.zoomForBounds;
import static org.mapsforge.core.util.MercatorProjection.calculateGroundResolution;
import static org.mapsforge.core.util.MercatorProjection.getMapSize;
import static org.mapsforge.map.scalebar.DefaultMapScaleBar.ScaleBarMode.SINGLE;
import static slash.navigation.base.RouteCharacteristics.Route;
import static slash.navigation.base.RouteCharacteristics.Waypoints;
import static slash.navigation.converter.gui.models.PositionColumns.DESCRIPTION_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.LATITUDE_COLUMN_INDEX;
import static slash.navigation.converter.gui.models.PositionColumns.LONGITUDE_COLUMN_INDEX;
import static slash.navigation.gui.helpers.JMenuHelper.createItem;
import static slash.navigation.gui.helpers.JTableHelper.isFirstToLastRow;
import static slash.navigation.maps.helpers.MapTransfer.asBoundingBox;
import static slash.navigation.maps.helpers.MapTransfer.asLatLong;
import static slash.navigation.maps.helpers.MapTransfer.asNavigationPosition;
import static slash.navigation.maps.helpers.MapTransfer.toBoundingBox;
import static slash.navigation.mapview.MapViewConstants.ROUTE_LINE_COLOR_PREFERENCE;
import static slash.navigation.mapview.MapViewConstants.ROUTE_LINE_WIDTH_PREFERENCE;
import static slash.navigation.mapview.MapViewConstants.TRACK_LINE_COLOR_PREFERENCE;
import static slash.navigation.mapview.MapViewConstants.TRACK_LINE_WIDTH_PREFERENCE;
import static slash.navigation.mapview.mapsforge.AwtGraphicMapView.GRAPHIC_FACTORY;
import static slash.navigation.mapview.mapsforge.models.LocalNames.MAP;

/**
 * Implementation for a component that displays the positions of a position list on a map
 * using the rewrite branch of the mapsforge project.
 *
 * @author Christian Pesch
 */

public class MapsforgeMapView implements MapView {
    private static final Preferences preferences = Preferences.userNodeForPackage(MapsforgeMapView.class);
    private static final Logger log = Logger.getLogger(MapsforgeMapView.class.getName());

    private static final String CENTER_LATITUDE_PREFERENCE = "centerLatitude";
    private static final String CENTER_LONGITUDE_PREFERENCE = "centerLongitude";
    private static final String CENTER_ZOOM_PREFERENCE = "centerZoom";
    private static final int SCROLL_DIFF = 100;

    private PositionsModel positionsModel;
    private PositionsSelectionModel positionsSelectionModel;
    private CharacteristicsModel characteristicsModel;
    private BooleanModel showAllPositionsAfterLoading;
    private BooleanModel recenterAfterZooming;
    private BooleanModel showCoordinates;
    private UnitSystemModel unitSystemModel;
    private MapViewCallbackOffline mapViewCallback;

    private PositionsModelListener positionsModelListener = new PositionsModelListener();
    private CharacteristicsModelListener characteristicsModelListener = new CharacteristicsModelListener();
    private MapViewCallbackListener mapViewCallbackListener = new MapViewCallbackListener();
    private ShowCoordinatesListener showCoordinatesListener = new ShowCoordinatesListener();
    private UnitSystemListener unitSystemListener = new UnitSystemListener();
    private DisplayedMapListener displayedMapListener = new DisplayedMapListener();
    private AppliedThemeListener appliedThemeListener = new AppliedThemeListener();

    private MapSelector mapSelector;
    private AwtGraphicMapView mapView;
    private MapViewMoverAndZoomer mapViewMoverAndZoomer;
    private MapViewCoordinateDisplayer mapViewCoordinateDisplayer = new MapViewCoordinateDisplayer();
    private static Bitmap markerIcon, waypointIcon;
    private static Paint ROUTE_NOT_VALID_PAINT, ROUTE_DOWNLOADING_PAINT;
    private File backgroundMap;
    private TileRendererLayer backgroundLayer;
    private SelectionUpdater selectionUpdater;
    private EventMapUpdater eventMapUpdater, routeUpdater, trackUpdater, waypointUpdater;
    private ExecutorService executor = newSingleThreadExecutor();

    // initialization

    public void initialize(PositionsModel positionsModel,
                           PositionsSelectionModel positionsSelectionModel,
                           CharacteristicsModel characteristicsModel,
                           MapViewCallback mapViewCallback,
                           BooleanModel showAllPositionsAfterLoading,
                           BooleanModel recenterAfterZooming,
                           BooleanModel showCoordinates,
                           BooleanModel showWaypointDescription,       /* ignored */
                           FixMapModeModel fixMapModeModel,            /* ignored */
                           UnitSystemModel unitSystemModel,            /* ignored */
                           GoogleMapsServerModel googleMapsServerModel /* ignored */) {
        this.mapViewCallback = (MapViewCallbackOffline) mapViewCallback;
        this.positionsModel = positionsModel;
        this.positionsSelectionModel = positionsSelectionModel;
        this.characteristicsModel = characteristicsModel;
        this.showAllPositionsAfterLoading = showAllPositionsAfterLoading;
        this.recenterAfterZooming = recenterAfterZooming;
        this.showCoordinates = showCoordinates;
        this.unitSystemModel = unitSystemModel;

        this.selectionUpdater = new SelectionUpdater(positionsModel, new SelectionOperation() {
            public void add(List<PositionWithLayer> positionWithLayers) {
                LatLong center = null;
                for (final PositionWithLayer positionWithLayer : positionWithLayers) {
                    if(!positionWithLayer.hasCoordinates())
                        continue;

                    LatLong latLong = asLatLong(positionWithLayer.getPosition());
                    Marker marker = new DraggableMarker(latLong, markerIcon, 8, -16) {
                        public void onDrop(LatLong latLong) {
                            int index = MapsforgeMapView.this.positionsModel.getIndex(positionWithLayer.getPosition());
                            MapsforgeMapView.this.positionsModel.edit(index, new PositionColumnValues(asList(LONGITUDE_COLUMN_INDEX, LATITUDE_COLUMN_INDEX),
                                    Arrays.<Object>asList(latLong.longitude, latLong.latitude)), true, true);
                            // ensure this marker is on top of the moved waypoint marker
                            getLayerManager().getLayers().remove(this);
                            mapView.addLayer(this);
                        }
                    };
                    positionWithLayer.setLayer(marker);
                    mapView.addLayer(marker);
                    center = latLong;
                }
                if (center != null)
                    setCenter(center, false);
            }

            public void remove(List<PositionWithLayer> positionWithLayers) {
                for (PositionWithLayer positionWithLayer : positionWithLayers) {
                    Layer layer = positionWithLayer.getLayer();
                    if (layer != null)
                        getLayerManager().getLayers().remove(layer);
                    else
                        log.warning("Could not find layer for selection position " + positionWithLayer);
                    positionWithLayer.setLayer(null);
                }
            }
        });

        this.routeUpdater = new TrackUpdater(positionsModel, new TrackOperation() {
            private List<PairWithLayer> pairs = new ArrayList<>();

            public void add(List<PairWithLayer> pairWithLayers) {
                internalAdd(pairWithLayers);
            }

            public void update(List<PairWithLayer> pairWithLayers) {
                internalRemove(pairWithLayers);
                internalAdd(pairWithLayers);
                updateSelectionAfterUpdate(pairWithLayers);
            }

            public void remove(List<PairWithLayer> pairWithLayers) {
                internalRemove(pairWithLayers);
                updateSelectionAfterRemove(pairWithLayers);
            }

            private void internalAdd(final List<PairWithLayer> pairWithLayers) {
                pairs.addAll(pairWithLayers);

                drawBeeline(pairWithLayers);
                fireDistanceAndTime();

                executor.execute(new Runnable() {
                    public void run() {
                        try {
                            RoutingService service = MapsforgeMapView.this.mapViewCallback.getRoutingService();
                            waitForInitialization(service);
                            waitForDownload(service);

                            drawRoute(pairWithLayers);
                            fireDistanceAndTime();
                        }
                        catch(Exception e) {
                            MapsforgeMapView.this.mapViewCallback.showRoutingException(e);
                        }
                    }

                    private void waitForInitialization(RoutingService service) {
                        if (!service.isInitialized()) {
                            while (!service.isInitialized()) {
                                try {
                                    sleep(100);
                                } catch (InterruptedException e) {
                                    // intentionally left empty
                                }
                            }
                        }
                    }

                    private void waitForDownload(RoutingService service) {
                        if (service.isDownload()) {
                            DownloadFuture future = service.downloadRoutingDataFor(asLongitudeAndLatitude(pairWithLayers));
                            if (future.isRequiresDownload()) {
                                MapsforgeMapView.this.mapViewCallback.showDownloadNotification();
                                future.download();
                            }
                            if (future.isRequiresProcessing()) {
                                MapsforgeMapView.this.mapViewCallback.showProcessNotification();
                                future.process();
                            }
                        }
                    }
                });
            }

            private void drawBeeline(List<PairWithLayer> pairsWithLayer) {
                int tileSize = mapView.getModel().displayModel.getTileSize();
                for (PairWithLayer pairWithLayer : pairsWithLayer) {
                    if(!pairWithLayer.hasCoordinates())
                        continue;

                    Line line = new Line(asLatLong(pairWithLayer.getFirst()), asLatLong(pairWithLayer.getSecond()), ROUTE_DOWNLOADING_PAINT, tileSize);
                    pairWithLayer.setLayer(line);
                    mapView.addLayer(line);

                    pairWithLayer.setDistance(pairWithLayer.getFirst().calculateDistance(pairWithLayer.getSecond()));
                    pairWithLayer.setTime(pairWithLayer.getFirst().calculateTime(pairWithLayer.getSecond()));
                }
            }

            private void drawRoute(List<PairWithLayer> pairWithLayers) {
                Paint routePaint = GRAPHIC_FACTORY.createPaint();
                routePaint.setColor(preferences.getInt(ROUTE_LINE_COLOR_PREFERENCE, 0x993379FF));
                routePaint.setStrokeWidth(preferences.getInt(ROUTE_LINE_WIDTH_PREFERENCE, 5));
                int tileSize = mapView.getModel().displayModel.getTileSize();
                RoutingService routingService = MapsforgeMapView.this.mapViewCallback.getRoutingService();
                for (PairWithLayer pairWithLayer : pairWithLayers) {
                    if(!pairWithLayer.hasCoordinates())
                        continue;

                    IntermediateRoute intermediateRoute = calculateRoute(routingService, pairWithLayer);
                    Polyline polyline = new Polyline(intermediateRoute.getLatLongs(), intermediateRoute.isValid() ? routePaint : ROUTE_NOT_VALID_PAINT, tileSize);
                    // remove beeline layer then add polyline layer from routing
                    removeLayer(pairWithLayer);
                    mapView.addLayer(polyline);
                    pairWithLayer.setLayer(polyline);
                }
            }

            private IntermediateRoute calculateRoute(RoutingService routingService, PairWithLayer pairWithLayer) {
                List<LatLong> latLongs = new ArrayList<>();
                latLongs.add(asLatLong(pairWithLayer.getFirst()));
                RoutingResult intermediate = routingService.getRouteBetween(pairWithLayer.getFirst(), pairWithLayer.getSecond(), MapsforgeMapView.this.mapViewCallback.getTravelMode());
                if (intermediate.isValid())
                    latLongs.addAll(asLatLong(intermediate.getPositions()));
                pairWithLayer.setDistance(intermediate.getDistance());
                pairWithLayer.setTime(intermediate.getTime());
                latLongs.add(asLatLong(pairWithLayer.getSecond()));
                return new IntermediateRoute(latLongs, intermediate.isValid());
            }

            private void removeLayer(PairWithLayer pairWithLayer) {
                Layer layer = pairWithLayer.getLayer();
                if (layer != null)
                    getLayerManager().getLayers().remove(layer);
                else
                    log.warning("Could not find layer for route pair " + pairWithLayer);
                pairWithLayer.setLayer(null);
            }

            private void internalRemove(List<PairWithLayer> pairWithLayers) {
                pairs.removeAll(pairWithLayers);

                for (PairWithLayer pairWithLayer : pairWithLayers) {
                    removeLayer(pairWithLayer);
                    pairWithLayer.setDistance(null);
                    pairWithLayer.setTime(null);
                }
                fireDistanceAndTime();
            }

            private void fireDistanceAndTime() {
                double totalDistance = 0.0;
                long totalTime = 0;
                for (PairWithLayer pairWithLayer : pairs) {
                    Double distance = pairWithLayer.getDistance();
                    if (distance != null)
                        totalDistance += distance;
                    Long time = pairWithLayer.getTime();
                    if (time != null)
                        totalTime += time;
                }
                fireCalculatedDistance((int) totalDistance, (int) (totalTime > 0 ? totalTime / 1000 : 0));
            }
        });

        this.trackUpdater = new TrackUpdater(positionsModel, new TrackOperation() {
            public void add(List<PairWithLayer> pairWithLayers) {
                internalAdd(pairWithLayers);
            }

            public void update(List<PairWithLayer> pairWithLayers) {
                internalRemove(pairWithLayers);
                internalAdd(pairWithLayers);
                updateSelectionAfterUpdate(pairWithLayers);
            }

            public void remove(List<PairWithLayer> pairWithLayers) {
                internalRemove(pairWithLayers);
                updateSelectionAfterRemove(pairWithLayers);
            }

            private void internalAdd(List<PairWithLayer> pairWithLayers) {
                Paint paint = GRAPHIC_FACTORY.createPaint();
                paint.setColor(preferences.getInt(TRACK_LINE_COLOR_PREFERENCE, 0xFF0000FF));
                paint.setStrokeWidth(preferences.getInt(TRACK_LINE_WIDTH_PREFERENCE, 2));
                int tileSize = mapView.getModel().displayModel.getTileSize();
                for (PairWithLayer pair : pairWithLayers) {
                    if(!pair.hasCoordinates())
                        continue;

                    Line line = new Line(asLatLong(pair.getFirst()), asLatLong(pair.getSecond()), paint, tileSize);
                    pair.setLayer(line);
                    mapView.addLayer(line);
                }
            }

            private void internalRemove(List<PairWithLayer> pairWithLayers) {
                for (PairWithLayer pairWithLayer : pairWithLayers) {
                    Layer layer = pairWithLayer.getLayer();
                    if (layer != null)
                        getLayerManager().getLayers().remove(layer);
                    else
                        log.warning("Could not find layer for track pair " + pairWithLayer);
                    pairWithLayer.setLayer(null);
                }
            }
        });

        this.waypointUpdater = new WaypointUpdater(positionsModel, new WaypointOperation() {
            public void add(List<PositionWithLayer> positionWithLayers) {
                for (PositionWithLayer positionWithLayer : positionWithLayers) {
                    internalAdd(positionWithLayer);
                }
            }

            public void update(List<PositionWithLayer> positionWithLayers) {
                List<NavigationPosition> updated = new ArrayList<>();
                for (PositionWithLayer positionWithLayer : positionWithLayers) {
                    internalRemove(positionWithLayer);
                    internalAdd(positionWithLayer);
                    updated.add(positionWithLayer.getPosition());
                }
                selectionUpdater.updatedPositions(new ArrayList<>(updated));
            }

            public void remove(List<PositionWithLayer> positionWithLayers) {
                List<NavigationPosition> removed = new ArrayList<>();
                for (PositionWithLayer positionWithLayer : positionWithLayers) {
                    internalRemove(positionWithLayer);
                    removed.add(positionWithLayer.getPosition());
                }
                selectionUpdater.removedPositions(removed);
            }

            private void internalAdd(PositionWithLayer positionWithLayer) {
                if(!positionWithLayer.hasCoordinates())
                    return;

                Marker marker = new Marker(asLatLong(positionWithLayer.getPosition()), waypointIcon, 1, 0);
                positionWithLayer.setLayer(marker);
                mapView.addLayer(marker);
            }

            private void internalRemove(PositionWithLayer positionWithLayer) {
                Layer layer = positionWithLayer.getLayer();
                if (layer != null)
                    getLayerManager().getLayers().remove(layer);
                else
                    log.warning("Could not find layer for position " + positionWithLayer);
                positionWithLayer.setLayer(null);
            }
        });

        this.eventMapUpdater = getEventMapUpdaterFor(Waypoints);

        positionsModel.addTableModelListener(positionsModelListener);
        characteristicsModel.addListDataListener(characteristicsModelListener);
        mapViewCallback.addChangeListener(mapViewCallbackListener);
        showCoordinates.addChangeListener(showCoordinatesListener);
        unitSystemModel.addChangeListener(unitSystemListener);

        initializeActions();
        initializeMapView();
    }

    private void initializeActions() {
        ActionManager actionManager = Application.getInstance().getContext().getActionManager();
        actionManager.register("select-position", new SelectPositionAction());
        actionManager.register("extend-selection", new ExtendSelectionAction());
        actionManager.register("add-position", new AddPositionAction());
        actionManager.register("delete-position-from-map", new DeletePositionAction());
        actionManager.registerLocal("delete", MAP, "delete-position-from-map");
        actionManager.register("center-here", new CenterAction());
        actionManager.register("zoom-in", new ZoomAction(+1));
        actionManager.register("zoom-out", new ZoomAction(-1));
    }

    private MapManager getMapManager() {
        return mapViewCallback.getMapManager();
    }

    private LayerManager getLayerManager() {
        return mapView.getLayerManager();
    }

    private void initializeMapView() {
        mapView = createMapView();
        handleUnitSystem();

        try {
            markerIcon = GRAPHIC_FACTORY.createResourceBitmap(MapsforgeMapView.class.getResourceAsStream("marker.png"), -1);
            waypointIcon = GRAPHIC_FACTORY.createResourceBitmap(MapsforgeMapView.class.getResourceAsStream("waypoint.png"), -1);
        } catch (IOException e) {
            log.severe("Cannot create marker and waypoint icon: " + e);
        }
        ROUTE_NOT_VALID_PAINT = GRAPHIC_FACTORY.createPaint();
        ROUTE_NOT_VALID_PAINT.setColor(0xFFFF0000);
        ROUTE_NOT_VALID_PAINT.setStrokeWidth(5);
        ROUTE_DOWNLOADING_PAINT = GRAPHIC_FACTORY.createPaint();
        ROUTE_DOWNLOADING_PAINT.setColor(0x993379FF);
        ROUTE_DOWNLOADING_PAINT.setStrokeWidth(5);
        ROUTE_DOWNLOADING_PAINT.setDashPathEffect(new float[]{3, 12});

        mapSelector = new MapSelector(getMapManager(), mapView);
        mapViewMoverAndZoomer = new MapViewMoverAndZoomer(mapView, getLayerManager());
        mapViewCoordinateDisplayer.initialize(mapView, mapViewCallback);
        new MapViewPopupMenu(mapView, createPopupMenu());

        final ActionManager actionManager = Application.getInstance().getContext().getActionManager();
        mapSelector.getMapViewPanel().registerKeyboardAction(new FrameAction() {
            public void run() {
                actionManager.run("zoom-in");
            }
        }, getKeyStroke(VK_PLUS, CTRL_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        mapSelector.getMapViewPanel().registerKeyboardAction(new FrameAction() {
            public void run() {
                actionManager.run("zoom-out");
            }
        }, getKeyStroke(VK_MINUS, CTRL_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        mapSelector.getMapViewPanel().registerKeyboardAction(new FrameAction() {
            public void run() {
                mapViewMoverAndZoomer.animateCenter(SCROLL_DIFF, 0);
            }
        }, getKeyStroke(VK_LEFT, CTRL_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        mapSelector.getMapViewPanel().registerKeyboardAction(new FrameAction() {
            public void run() {
                mapViewMoverAndZoomer.animateCenter(-SCROLL_DIFF, 0);
            }
        }, getKeyStroke(VK_RIGHT, CTRL_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        mapSelector.getMapViewPanel().registerKeyboardAction(new FrameAction() {
            public void run() {
                mapViewMoverAndZoomer.animateCenter(0, SCROLL_DIFF);
            }
        }, getKeyStroke(VK_UP, CTRL_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);
        mapSelector.getMapViewPanel().registerKeyboardAction(new FrameAction() {
            public void run() {
                mapViewMoverAndZoomer.animateCenter(0, -SCROLL_DIFF);
            }
        }, getKeyStroke(VK_DOWN, CTRL_DOWN_MASK), WHEN_IN_FOCUSED_WINDOW);

        final MapViewPosition mapViewPosition = mapView.getModel().mapViewPosition;
        mapViewPosition.addObserver(new Observer() {
            public void onChange() {
                mapSelector.zoomChanged(mapViewPosition.getZoomLevel());
            }
        });

        double longitude = preferences.getDouble(CENTER_LONGITUDE_PREFERENCE, -25.0);
        double latitude = preferences.getDouble(CENTER_LATITUDE_PREFERENCE, 35.0);
        byte zoom = (byte) preferences.getInt(CENTER_ZOOM_PREFERENCE, 2);
        mapViewPosition.setMapPosition(new MapPosition(new LatLong(latitude, longitude), zoom));

        mapView.getModel().mapViewDimension.addObserver(new Observer() {
            private boolean initialized = false;

            public void onChange() {
                if (!initialized) {
                    handleMapAndThemeUpdate(true, true);
                    initialized = true;
                }
            }
        });

        getMapManager().getDisplayedMapModel().addChangeListener(displayedMapListener);
        getMapManager().getAppliedThemeModel().addChangeListener(appliedThemeListener);
    }

    public void setBackgroundMap(File backgroundMap) {
        this.backgroundMap = backgroundMap;
        updateMapAndThemesAfterDirectoryScanning();
    }

    public void updateMapAndThemesAfterDirectoryScanning() {
        if (mapView != null)
            handleMapAndThemeUpdate(false, false);
    }

    private AwtGraphicMapView createMapView() {
        final AwtGraphicMapView mapView = new AwtGraphicMapView();
        new MapViewResizer(mapView, mapView.getModel().mapViewDimension);
        mapView.getMapScaleBar().setVisible(true);
        ((DefaultMapScaleBar) mapView.getMapScaleBar()).setScaleBarMode(SINGLE);
        return mapView;
    }

    private void handleUnitSystem() {
        UnitSystem unitSystem = unitSystemModel.getUnitSystem();
        switch(unitSystem) {
            case Metric:
                mapView.getMapScaleBar().setDistanceUnitAdapter(MetricUnitAdapter.INSTANCE);
                break;
            case Statute:
                mapView.getMapScaleBar().setDistanceUnitAdapter(ImperialUnitAdapter.INSTANCE);
                break;
            case Nautic:
                mapView.getMapScaleBar().setDistanceUnitAdapter(NauticalUnitAdapter.INSTANCE);
                break;
            default:
                throw new IllegalArgumentException("Unknown UnitSystem " + unitSystem);
        }
    }

    private JPopupMenu createPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(createItem("select-position"));
        menu.add(createItem("add-position"));    // TODO should be "new-position"
        menu.add(createItem("delete-position-from-map"));
        menu.addSeparator();
        menu.add(createItem("center-here"));
        menu.add(createItem("zoom-in"));
        menu.add(createItem("zoom-out"));
        return menu;
    }

    private TileRendererLayer createTileRendererLayer(File map) {
        TileRendererLayer tileRendererLayer = new TileRendererLayer(createTileCache(), new MapFile(map), mapView.getModel().mapViewPosition, true, true, GRAPHIC_FACTORY);
        tileRendererLayer.setXmlRenderTheme(getMapManager().getAppliedThemeModel().getItem().getXmlRenderTheme());
        return tileRendererLayer;
    }

    private TileDownloadLayer createTileDownloadLayer(TileSource tileSource) {
        return new TileDownloadLayer(createTileCache(), mapView.getModel().mapViewPosition, tileSource, GRAPHIC_FACTORY);
    }

    private TileCache createTileCache() {
        // TODO think about replacing with file system cache that survives restarts
        // File cacheDirectory = new File(System.getProperty("java.io.tmpdir"), "mapsforge");
        // TileCache secondLevelTileCache = new FileSystemTileCache(1024, cacheDirectory, GRAPHIC_FACTORY);
        // return new TwoLevelTileCache(firstLevelTileCache, secondLevelTileCache);
        return new InMemoryTileCache(64);
    }

    private void updateSelectionAfterUpdate(List<PairWithLayer> pairWithLayers) {
        Set<NavigationPosition> updated = new HashSet<>();
        for (PairWithLayer pair : pairWithLayers) {
            updated.add(pair.getFirst());
            updated.add(pair.getSecond());
        }
        selectionUpdater.updatedPositions(new ArrayList<>(updated));
    }

    private void updateSelectionAfterRemove(List<PairWithLayer> pairWithLayers) {
        Set<NavigationPosition> removed = new HashSet<>();
        for (PairWithLayer pair : pairWithLayers) {
            removed.add(pair.getFirst());
            removed.add(pair.getSecond());
        }
        selectionUpdater.removedPositions(new ArrayList<>(removed));
    }

    private java.util.Map<LocalMap, Layer> mapsToLayers = new HashMap<>();

    public void handleMapAndThemeUpdate(boolean centerAndZoom, boolean alwaysRecenter) {
        Layers layers = getLayerManager().getLayers();

        // add new map with a theme
        LocalMap map = getMapManager().getDisplayedMapModel().getItem();
        Layer layer;
        try {
            layer = map.isVector() ? createTileRendererLayer(map.getFile()) : createTileDownloadLayer(map.getTileSource());
        } catch (Exception e) {
            mapViewCallback.showMapException(map != null ? map.getDescription() : "<no map>", e);
            return;
        }

        // remove old map
        for (Map.Entry<LocalMap, Layer> entry : mapsToLayers.entrySet()) {
            Layer remove = entry.getValue();
            layers.remove(remove);
            remove.onDestroy();
        }
        mapsToLayers.clear();

        // add map as the first to be behind all additional layers
        layers.add(0, layer);
        mapsToLayers.put(map, layer);

        // initialize tile renderer layer for background map
        if (backgroundMap != null) {
            backgroundLayer = createTileRendererLayer(backgroundMap);
            backgroundMap = null;
        }
        if(backgroundLayer != null) {
            layers.remove(backgroundLayer);
            if (map.isVector())
                layers.add(0, backgroundLayer);
        }

        // then start download layer threads
        if (layer instanceof TileDownloadLayer)
            ((TileDownloadLayer) layer).start();

        // center and zoom: if map is initialized, doesn't contain route or there is no route
        BoundingBox mapBoundingBox = getMapBoundingBox();
        BoundingBox routeBoundingBox = getRouteBoundingBox();
        if (centerAndZoom &&
                ((mapBoundingBox != null && routeBoundingBox != null && !mapBoundingBox.contains(routeBoundingBox)) ||
                        routeBoundingBox == null)) {
            centerAndZoom(mapBoundingBox, routeBoundingBox, alwaysRecenter);
        }
        limitZoomLevel();
        log.info("Using map " + mapsToLayers.keySet() + " and theme " + getMapManager().getAppliedThemeModel().getItem() + " with zoom " + getZoom());
    }

    private BaseRoute lastRoute = null;
    private RouteCharacteristics lastCharacteristics = Waypoints; // corresponds to default eventMapUpdater

    private void updateRouteButDontRecenter() {
        // avoid duplicate work
        RouteCharacteristics characteristics = MapsforgeMapView.this.characteristicsModel.getSelectedCharacteristics();
        BaseRoute route = positionsModel.getRoute();
        if (lastCharacteristics.equals(characteristics) && lastRoute != null && lastRoute.equals(positionsModel.getRoute()))
            return;
        lastCharacteristics = characteristics;
        lastRoute = route;
        replaceRoute();
    }

    private void replaceRoute() {
        // throw away running routing executions      // TODO use signals later
        List<Runnable> runnables = executor.shutdownNow();
        if (runnables.size() > 0)
            log.info("Replacing route stopped runnables: " + runnables);
        executor = newSingleThreadExecutor();

        // remove all from previous event map updater
        eventMapUpdater.handleRemove(0, MAX_VALUE);

        // select current event map updater and let him add all
        eventMapUpdater = getEventMapUpdaterFor(lastCharacteristics);
        eventMapUpdater.handleAdd(0, positionsModel.getRowCount() - 1);
    }

    private EventMapUpdater getEventMapUpdaterFor(RouteCharacteristics characteristics) {
        switch (characteristics) {
            case Route:
                return routeUpdater;
            case Track:
                return trackUpdater;
            case Waypoints:
                return waypointUpdater;
            default:
                throw new IllegalArgumentException("RouteCharacteristics " + characteristics + " is not supported");
        }
    }

    public boolean isInitialized() {
        return true;
    }

    public boolean isDownload() {
        return true;
    }

    public Throwable getInitializationCause() {
        return null;
    }

    public void dispose() {
        getMapManager().getDisplayedMapModel().removeChangeListener(displayedMapListener);
        getMapManager().getAppliedThemeModel().removeChangeListener(appliedThemeListener);

        positionsModel.removeTableModelListener(positionsModelListener);
        characteristicsModel.removeListDataListener(characteristicsModelListener);
        mapViewCallback.removeChangeListener(mapViewCallbackListener);
        unitSystemModel.removeChangeListener(unitSystemListener);

        NavigationPosition center = getCenter();
        preferences.putDouble(CENTER_LONGITUDE_PREFERENCE, center.getLongitude());
        preferences.putDouble(CENTER_LATITUDE_PREFERENCE, center.getLatitude());
        int zoom = getZoom();
        preferences.putInt(CENTER_ZOOM_PREFERENCE, zoom);

        executor.shutdownNow();
        mapView.destroyAll();
    }

    public Component getComponent() {
        return mapSelector.getComponent();
    }

    public void resize() {
        // intentionally left empty
    }

    @SuppressWarnings("unchecked")
    public void showAllPositions() {
        List<NavigationPosition> positions = positionsModel.getRoute().getPositions();
        if (positions.size() > 0) {
            BoundingBox both = new BoundingBox(positions);
            zoomToBounds(both);
            setCenter(both.getCenter(), true);
        }
    }

    private Polyline mapBorder, routeBorder;

    public void showMapBorder(BoundingBox mapBoundingBox) {
        if (mapBorder != null) {
            getLayerManager().getLayers().remove(mapBorder);
            mapBorder = null;
        }
        if (routeBorder != null) {
            getLayerManager().getLayers().remove(routeBorder);
            routeBorder = null;
        }

        if (mapBoundingBox != null) {
            mapBorder = drawBorder(mapBoundingBox);

            BoundingBox routeBoundingBox = getRouteBoundingBox();
            if (routeBoundingBox != null)
                routeBorder = drawBorder(routeBoundingBox);

            centerAndZoom(mapBoundingBox, routeBoundingBox, true);
        }
    }

    private Polyline drawBorder(BoundingBox boundingBox) {
        Paint paint = GRAPHIC_FACTORY.createPaint();
        paint.setColor(BLUE);
        paint.setStrokeWidth(3);
        paint.setDashPathEffect(new float[]{3, 12});
        Polyline polyline = new Polyline(asLatLong(boundingBox), paint, mapView.getModel().displayModel.getTileSize());
        mapView.addLayer(polyline);
        return polyline;
    }

    private BoundingBox getMapBoundingBox() {
        Collection<Layer> values = mapsToLayers.values();
        if (!values.isEmpty()) {
            Layer layer = values.iterator().next();
            if (layer instanceof TileRendererLayer) {
                TileRendererLayer tileRendererLayer = (TileRendererLayer) layer;
                return toBoundingBox(tileRendererLayer.getMapDataStore().boundingBox());
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private BoundingBox getRouteBoundingBox() {
        BaseRoute route = positionsModel.getRoute();
        return route != null && route.getPositions().size() > 0 ? new BoundingBox(route.getPositions()) : null;
    }

    private void centerAndZoom(BoundingBox mapBoundingBox, BoundingBox routeBoundingBox, boolean alwaysRecenter) {
        List<NavigationPosition> positions = new ArrayList<>();

        // if there is a route and we center and zoom, then use the route bounding box
        if (routeBoundingBox != null) {
            positions.add(routeBoundingBox.getNorthEast());
            positions.add(routeBoundingBox.getSouthWest());
        }

        // if the map is limited
        if (mapBoundingBox != null) {

            // if there is a route
            if (routeBoundingBox != null) {
                positions.add(routeBoundingBox.getNorthEast());
                positions.add(routeBoundingBox.getSouthWest());
                // if the map is limited and doesn't cover the route
                if (!mapBoundingBox.contains(routeBoundingBox)) {
                    positions.add(mapBoundingBox.getNorthEast());
                    positions.add(mapBoundingBox.getSouthWest());
                }

                // if there just a map
            } else {
                positions.add(mapBoundingBox.getNorthEast());
                positions.add(mapBoundingBox.getSouthWest());
            }
        }

        if (positions.size() > 0) {
            BoundingBox both = new BoundingBox(positions);
            zoomToBounds(both);
            setCenter(both.getCenter(), alwaysRecenter);
        }
    }

    private void limitZoomLevel() {
        // limit minimum zoom to prevent zooming out too much and losing the map
        byte zoomLevelMin = 2;
        LocalMap map = mapsToLayers.keySet().iterator().next();
        if (map.isVector() && mapView.getModel().mapViewDimension.getDimension() != null)
            zoomLevelMin = (byte) max(0, zoomForBounds(mapView.getModel().mapViewDimension.getDimension(),
                    asBoundingBox(map.getBoundingBox()), mapView.getModel().displayModel.getTileSize()) - 3);
        mapView.getModel().mapViewPosition.setZoomLevelMin(zoomLevelMin);

        // limit maximum to prevent zooming in to grey area
        byte zoomLevelMax = (byte) (map.isVector() ? 22 : 18);
        mapView.getModel().mapViewPosition.setZoomLevelMax(zoomLevelMax);
    }

    private LongitudeAndLatitude asLongitudeAndLatitude(NavigationPosition position) {
        return new LongitudeAndLatitude(position.getLongitude(), position.getLatitude());
    }

    private List<LongitudeAndLatitude> asLongitudeAndLatitude(List<PairWithLayer> pairWithLayers) {
        List<LongitudeAndLatitude> result = new ArrayList<>();
        for (PairWithLayer pairWithLayer : pairWithLayers) {
            if(!pairWithLayer.hasCoordinates())
                continue;

            result.add(asLongitudeAndLatitude(pairWithLayer.getFirst()));
            result.add(asLongitudeAndLatitude(pairWithLayer.getSecond()));
        }
        return result;
    }

    private boolean isVisible(LatLong latLong, int border) {
        MapViewProjection projection = new MapViewProjection(mapView);
        LatLong upperLeft = projection.fromPixels(border, border);
        Dimension dimension = mapView.getDimension();
        LatLong lowerRight = projection.fromPixels(dimension.width - border, dimension.height - border);
        return upperLeft != null && lowerRight != null && new org.mapsforge.core.model.BoundingBox(lowerRight.latitude, upperLeft.longitude, upperLeft.latitude, lowerRight.longitude).contains(latLong);
    }

    public NavigationPosition getCenter() {
        return asNavigationPosition(mapView.getModel().mapViewPosition.getCenter());
    }

    private void setCenter(LatLong center, boolean alwaysRecenter) {
        if (alwaysRecenter || recenterAfterZooming.getBoolean() || !isVisible(center, 20))
            mapView.getModel().mapViewPosition.animateTo(center);
    }

    private void setCenter(NavigationPosition center, boolean alwaysRecenter) {
        setCenter(asLatLong(center), alwaysRecenter);
    }

    private int getZoom() {
        return mapView.getModel().mapViewPosition.getZoomLevel();
    }

    private void setZoom(int zoom) {
        mapView.setZoomLevel((byte) zoom);
    }

    private void zoomToBounds(org.mapsforge.core.model.BoundingBox boundingBox) {
        Dimension dimension = mapView.getModel().mapViewDimension.getDimension();
        if (dimension == null)
            return;
        byte zoom = zoomForBounds(dimension, boundingBox, mapView.getModel().displayModel.getTileSize());
        setZoom(zoom);
    }

    private void zoomToBounds(BoundingBox boundingBox) {
        zoomToBounds(asBoundingBox(boundingBox));
    }

    public boolean isSupportsPrinting() {
        return false;
    }

    public boolean isSupportsPrintingWithDirections() {
        return false;
    }

    public void print(String title, boolean withDirections) {
        throw new UnsupportedOperationException("Printing not supported");
    }

    public void setSelectedPositions(int[] selectedPositions, boolean replaceSelection) {
        if (selectionUpdater == null)
            return;
        selectionUpdater.setSelectedPositions(selectedPositions, replaceSelection);
    }

    private LatLong getMousePosition() {
        Point point = mapViewMoverAndZoomer.getLastMousePoint();
        return point != null ? new MapViewProjection(mapView).fromPixels(point.getX(), point.getY()) :
                mapView.getModel().mapViewPosition.getCenter();
    }

    private double getThresholdForPixel(LatLong latLong, int pixel) {
        long mapSize = getMapSize(mapView.getModel().mapViewPosition.getZoomLevel(), mapView.getModel().displayModel.getTileSize());
        double metersPerPixel = calculateGroundResolution(latLong.latitude, mapSize);
        return metersPerPixel * pixel;
    }

    private void selectPosition(Double longitude, Double latitude, Double threshold, boolean replaceSelection) {
        int row = positionsModel.getClosestPosition(longitude, latitude, threshold);
        if (row != -1 && !mapViewMoverAndZoomer.isMousePressedOnMarker())
            positionsSelectionModel.setSelectedPositions(new int[]{row}, replaceSelection);
    }

    private class SelectPositionAction extends FrameAction {
        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                Double threshold = getThresholdForPixel(latLong, 15);
                selectPosition(latLong.longitude, latLong.latitude, threshold, true);
            }
        }
    }

    private class ExtendSelectionAction extends FrameAction {
        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                Double threshold = getThresholdForPixel(latLong, 15);
                selectPosition(latLong.longitude, latLong.latitude, threshold, false);
            }
        }
    }

    private class AddPositionAction extends FrameAction {
        private int getAddRow() {
            List<PositionWithLayer> lastSelectedPositions = selectionUpdater.getPositionWithLayers();
            NavigationPosition position = lastSelectedPositions.size() > 0 ? lastSelectedPositions.get(lastSelectedPositions.size() - 1).getPosition() : null;
            // quite crude logic to be as robust as possible on failures
            if (position == null && positionsModel.getRowCount() > 0)
                position = positionsModel.getPosition(positionsModel.getRowCount() - 1);
            return position != null ? positionsModel.getIndex(position) + 1 : 0;
        }

        private void insertPosition(int row, Double longitude, Double latitude) {
            positionsModel.add(row, longitude, latitude, null, null, null, mapViewCallback.createDescription(positionsModel.getRowCount() + 1, null));
            int[] rows = new int[]{row};
            positionsSelectionModel.setSelectedPositions(rows, true);
            mapViewCallback.complementData(rows, true, true, true, true, false);
        }

        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                int row = getAddRow();
                insertPosition(row, latLong.longitude, latLong.latitude);
            }
        }
    }

    private class DeletePositionAction extends FrameAction {
        private void removePosition(Double longitude, Double latitude, Double threshold) {
            int row = positionsModel.getClosestPosition(longitude, latitude, threshold);
            if (row != -1) {
                positionsModel.remove(new int[]{row});
            }
        }

        public void run() {
            LatLong latLong = getMousePosition();
            if (latLong != null) {
                Double threshold = getThresholdForPixel(latLong, 15);
                removePosition(latLong.longitude, latLong.latitude, threshold);
            }
        }
    }

    private class CenterAction extends FrameAction {
        public void run() {
            mapViewMoverAndZoomer.centerToMousePosition();
        }
    }

    private class ZoomAction extends FrameAction {
        private byte zoomLevelDiff;

        private ZoomAction(int zoomLevelDiff) {
            this.zoomLevelDiff = (byte) zoomLevelDiff;
        }

        public void run() {
            mapViewMoverAndZoomer.zoomToMousePosition(zoomLevelDiff);
        }
    }

    // listeners

    private final List<MapViewListener> mapViewListeners = new CopyOnWriteArrayList<>();

    public void addMapViewListener(MapViewListener listener) {
        mapViewListeners.add(listener);
    }

    public void removeMapViewListener(MapViewListener listener) {
        mapViewListeners.remove(listener);
    }

    private void fireCalculatedDistance(int meters, int seconds) {
        for (MapViewListener listener : mapViewListeners) {
            listener.calculatedDistance(meters, seconds);
        }
    }

    private class MapViewCallbackListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            if (positionsModel.getRoute().getCharacteristics().equals(Route))
                replaceRoute();
        }
    }

    private class PositionsModelListener implements TableModelListener {
        public void tableChanged(TableModelEvent e) {
            switch (e.getType()) {
                case INSERT:
                    eventMapUpdater.handleAdd(e.getFirstRow(), e.getLastRow());
                    break;
                case UPDATE:
                    if (positionsModel.isContinousRange())
                        return;
                    if (!(e.getColumn() == DESCRIPTION_COLUMN_INDEX ||
                            e.getColumn() == LONGITUDE_COLUMN_INDEX ||
                            e.getColumn() == LATITUDE_COLUMN_INDEX ||
                            e.getColumn() == ALL_COLUMNS))
                        return;

                    boolean allRowsChanged = isFirstToLastRow(e);
                    if (!allRowsChanged)
                        eventMapUpdater.handleUpdate(e.getFirstRow(), e.getLastRow());
                    if (allRowsChanged && showAllPositionsAfterLoading.getBoolean())
                        centerAndZoom(getMapBoundingBox(), getRouteBoundingBox(), true);

                    break;
                case DELETE:
                    eventMapUpdater.handleRemove(e.getFirstRow(), e.getLastRow());
                    break;
                default:
                    throw new IllegalArgumentException("Event type " + e.getType() + " is not supported");
            }
        }
    }

    private class CharacteristicsModelListener implements ListDataListener {
        public void intervalAdded(ListDataEvent e) {
        }

        public void intervalRemoved(ListDataEvent e) {
        }

        public void contentsChanged(ListDataEvent e) {
            updateRouteButDontRecenter();
        }
    }

    private class ShowCoordinatesListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            mapViewCoordinateDisplayer.setShowCoordinates(showCoordinates.getBoolean());
        }
    }

    private class UnitSystemListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            handleUnitSystem();
        }
    }

    private class DisplayedMapListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            handleMapAndThemeUpdate(true, !isVisible(mapView.getModel().mapViewPosition.getCenter(), 20));
        }
    }

    private class AppliedThemeListener implements ChangeListener {
        public void stateChanged(ChangeEvent e) {
            handleMapAndThemeUpdate(false, false);
        }
    }
}
