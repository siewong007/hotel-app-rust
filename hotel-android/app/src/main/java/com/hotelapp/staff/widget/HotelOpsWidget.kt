package com.hotelapp.staff.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.hotelapp.staff.HotelStaffApp
import com.hotelapp.staff.MainActivity
import com.hotelapp.staff.core.network.HotelJson
import java.text.DateFormat
import java.util.Date

class HotelOpsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent {
            GlanceTheme {
                OpsWidgetContent(context = context, snapshot = snapshot)
            }
        }
    }

    private suspend fun loadSnapshot(context: Context): OpsWidgetSnapshot {
        val app = context.applicationContext as? HotelStaffApp ?: return OpsWidgetSnapshot()
        val raw = app.container.localStore.getCache(OpsWidgetSnapshot.CACHE_KEY)?.payloadJson ?: return OpsWidgetSnapshot()
        return runCatching {
            HotelJson.decodeFromString(OpsWidgetSnapshot.serializer(), raw)
        }.getOrDefault(OpsWidgetSnapshot())
    }
}

@Composable
private fun OpsWidgetContent(
    context: Context,
    snapshot: OpsWidgetSnapshot,
) {
    val openDashboardIntent = deepLinkIntent(context, "hotelstaff://dashboard")

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(openDashboardIntent))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = snapshot.hotelName,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                if (snapshot.isAuthenticated && snapshot.updatedAtMs > 0L) {
                    val timeStr = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.updatedAtMs))
                    Text(
                        text = "Updated $timeStr",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
            if (snapshot.isAuthenticated) {
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(10.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${snapshot.occupancyPercent} (${snapshot.occupiedRooms}/${snapshot.totalRooms})",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(10.dp))

        if (!snapshot.isAuthenticated) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(12.dp)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Sign in to view live occupancy, arrivals, and room status",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        } else {
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WidgetStatTile(
                    label = "Arriving",
                    value = snapshot.arrivingToday.toString(),
                    intent = deepLinkIntent(context, "hotelstaff://bookings?view=arriving"),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetStatTile(
                    label = "Dirty Rooms",
                    value = snapshot.dirtyRooms.toString(),
                    intent = deepLinkIntent(context, "hotelstaff://rooms"),
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(modifier = GlanceModifier.width(8.dp))
                WidgetStatTile(
                    label = "Open Maint.",
                    value = snapshot.openMaintenance.toString(),
                    intent = deepLinkIntent(context, "hotelstaff://maintenance"),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}

@Composable
private fun WidgetStatTile(
    label: String,
    value: String,
    intent: Intent,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(12.dp)
            .clickable(actionStartActivity(intent))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 10.sp,
            ),
            maxLines = 1,
        )
    }
}

private fun deepLinkIntent(context: Context, uriString: String): Intent =
    Intent(Intent.ACTION_VIEW, uriString.toUri(), context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
