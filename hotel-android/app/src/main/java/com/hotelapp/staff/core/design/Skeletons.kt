package com.hotelapp.staff.core.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

@Composable
fun rememberShimmerBrush(): Brush {
    val colors = HotelTheme.colors
    val reducedMotion = rememberReducedMotion()
    if (reducedMotion) {
        return SolidColor(colors.surfaceRaised)
    }

    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1250, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_offset",
    )

    return Brush.linearGradient(
        colors = listOf(
            colors.surfaceRaised,
            colors.surfaceHover,
            colors.surfaceRaised,
        ),
        start = Offset(progress - 360f, 0f),
        end = Offset(progress, 240f),
    )
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    brush: Brush = rememberShimmerBrush(),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush),
    )
}

@Composable
fun DashboardSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        userScrollEnabled = false,
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBox(modifier = Modifier.width(170.dp).height(22.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp), brush = brush)
                }
                SkeletonBox(modifier = Modifier.size(44.dp), shape = CircleShape, brush = brush)
            }
        }
        item {
            HotelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkeletonBox(modifier = Modifier.width(110.dp).height(14.dp), brush = brush)
                        SkeletonBox(modifier = Modifier.width(84.dp).height(34.dp), brush = brush)
                    }
                    SkeletonBox(modifier = Modifier.width(88.dp).height(24.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                }
                Spacer(modifier = Modifier.height(12.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(10.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SkeletonBox(modifier = Modifier.width(88.dp).height(14.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(128.dp).height(14.dp), brush = brush)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        repeat(2) {
                            HotelCard(modifier = Modifier.weight(1f)) {
                                SkeletonBox(modifier = Modifier.width(84.dp).height(12.dp), brush = brush)
                                Spacer(modifier = Modifier.height(10.dp))
                                SkeletonBox(modifier = Modifier.width(52.dp).height(28.dp), brush = brush)
                                Spacer(modifier = Modifier.height(6.dp))
                                SkeletonBox(modifier = Modifier.width(100.dp).height(12.dp), brush = brush)
                            }
                        }
                    }
                }
                HotelCard {
                    SkeletonBox(modifier = Modifier.width(140.dp).height(14.dp), brush = brush)
                    Spacer(modifier = Modifier.height(8.dp))
                    SkeletonBox(modifier = Modifier.width(120.dp).height(26.dp), brush = brush)
                }
            }
        }
        items(2) {
            HotelCard {
                SkeletonBox(modifier = Modifier.width(148.dp).height(18.dp), brush = brush)
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.width(190.dp).height(14.dp), brush = brush)
            }
        }
    }
}

@Composable
fun BookingListSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    val brush = rememberShimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            HotelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(modifier = Modifier.width(120.dp).height(16.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(74.dp).height(22.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                }
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.width(180.dp).height(18.dp), brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.75f).height(14.dp), brush = brush)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(modifier = Modifier.width(90.dp).height(22.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(76.dp).height(18.dp), brush = brush)
                }
            }
        }
    }
}

@Composable
fun RoomGridSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 8,
) {
    val brush = rememberShimmerBrush()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            HotelCard {
                SkeletonBox(modifier = Modifier.width(64.dp).height(24.dp), brush = brush)
                Spacer(modifier = Modifier.height(6.dp))
                SkeletonBox(modifier = Modifier.width(96.dp).height(14.dp), brush = brush)
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.width(82.dp).height(22.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                Spacer(modifier = Modifier.height(10.dp))
                SkeletonBox(modifier = Modifier.width(72.dp).height(12.dp), brush = brush)
            }
        }
    }
}

@Composable
fun HousekeepingSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    ListCardSkeleton(modifier = modifier, count = count)
}

@Composable
fun ListCardSkeleton(
    modifier: Modifier = Modifier,
    count: Int = 5,
) {
    val brush = rememberShimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(count) {
            HotelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeletonBox(modifier = Modifier.width(136.dp).height(18.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(72.dp).height(22.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                }
                Spacer(modifier = Modifier.height(8.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp), brush = brush)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    SkeletonBox(modifier = Modifier.width(84.dp).height(20.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                    SkeletonBox(modifier = Modifier.width(76.dp).height(14.dp), brush = brush)
                }
            }
        }
    }
}

@Composable
fun DetailScreenSkeleton(modifier: Modifier = Modifier) {
    val brush = rememberShimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        item {
            HotelCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBox(modifier = Modifier.size(48.dp), shape = CircleShape, brush = brush)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SkeletonBox(modifier = Modifier.width(160.dp).height(20.dp), brush = brush)
                        SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp), brush = brush)
                    }
                    SkeletonBox(modifier = Modifier.width(74.dp).height(24.dp), shape = RoundedCornerShape(999.dp), brush = brush)
                }
                Spacer(modifier = Modifier.height(14.dp))
                SkeletonBox(modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(12.dp), brush = brush)
                Spacer(modifier = Modifier.height(12.dp))
                repeat(4) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SkeletonBox(modifier = Modifier.width(84.dp).height(14.dp), brush = brush)
                        SkeletonBox(modifier = Modifier.width(120.dp).height(14.dp), brush = brush)
                    }
                }
            }
        }
        items(2) {
            HotelCard {
                SkeletonBox(modifier = Modifier.width(130.dp).height(18.dp), brush = brush)
                Spacer(modifier = Modifier.height(10.dp))
                repeat(3) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SkeletonBox(modifier = Modifier.width(92.dp).height(14.dp), brush = brush)
                        SkeletonBox(modifier = Modifier.width(104.dp).height(14.dp), brush = brush)
                    }
                }
            }
        }
    }
}
