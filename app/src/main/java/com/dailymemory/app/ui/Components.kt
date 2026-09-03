package com.dailymemory.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BlushCard(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    borderColor: Color = JournalColors.Border,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = color,
        contentColor = JournalColors.Ink,
        border = BorderStroke(2.dp, borderColor),
        tonalElevation = 0.dp,
    ) { Column(content = content) }
}

@Composable
fun JournalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, tween(110))
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 46.dp).scale(scale),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = JournalColors.Coral, contentColor = Color.White),
        border = BorderStroke(1.4.dp, if (enabled) JournalColors.Ink else JournalColors.Border),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun JournalOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, tween(110))
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 46.dp).scale(scale),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = JournalColors.Ink),
        border = BorderStroke(1.5.dp, JournalColors.Border),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        interactionSource = interaction,
        content = content,
    )
}

@Composable
fun JournalDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        shape = RoundedCornerShape(26.dp),
        containerColor = JournalColors.Background,
        titleContentColor = JournalColors.Ink,
        textContentColor = JournalColors.Ink,
        tonalElevation = 0.dp,
        modifier = Modifier.border(2.dp, JournalColors.Border, RoundedCornerShape(26.dp)),
    )
}

@Composable
fun FruitNavigationBar(labels: List<String>, selected: Int, onSelected: (Int) -> Unit) {
    val fruits = listOf(Fruit.PLUM, Fruit.ORANGE, Fruit.APPLE, Fruit.PEACH)
    val labelBand = maxOf(24f, 20f * LocalDensity.current.fontScale).dp
    Row(
        Modifier.fillMaxWidth().height(42.dp + labelBand).drawBehind {
            val bandHeight = labelBand.toPx()
            drawRect(JournalColors.Background)
            drawRect(JournalColors.Navigation, Offset(0f, size.height-bandHeight), Size(size.width,bandHeight))
            drawLine(JournalColors.Ink.copy(alpha=.28f),Offset(0f,size.height-bandHeight),Offset(size.width,size.height-bandHeight),1.dp.toPx())
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(if(pressed) .9f else 1f, tween(120))
            Column(
                Modifier.weight(1f).fillMaxHeight()
                    .selectable(selected == index, interactionSource = interaction, indication = null, role = Role.Tab) { onSelected(index) }
                    .padding(top=3.dp).scale(scale),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FruitIcon(fruits[index], selected == index, Modifier.size(39.dp))
                Text(label, fontSize=12.sp, lineHeight=18.sp,
                    fontWeight=if(selected==index) FontWeight.Medium else FontWeight.Normal,
                    color=if(selected==index) JournalColors.Ink else Color(0xFFB7807A))
            }
        }
    }
}

@Composable
fun JournalAddButton(description: String, onClick: () -> Unit) {
    val interaction=remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed) .91f else 1f, tween(100))
    Box(
        Modifier.padding(end=2.dp,bottom=14.dp).size(68.dp).scale(scale)
            .clip(CircleShape).background(JournalColors.Coral)
            .border(3.dp,JournalColors.Ink,CircleShape)
            .clickable(interactionSource=interaction, indication=null, role=Role.Button, onClick=onClick)
            .semantics { contentDescription=description },
        contentAlignment=Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            drawLine(Color.White,Offset(size.width/2,2.dp.toPx()),Offset(size.width/2,size.height-2.dp.toPx()),3.8.dp.toPx(),StrokeCap.Round)
            drawLine(Color.White,Offset(2.dp.toPx(),size.height/2),Offset(size.width-2.dp.toPx(),size.height/2),3.8.dp.toPx(),StrokeCap.Round)
        }
    }
}

@Composable
fun JournalTopBar(title: String, onBack: () -> Unit, onAction: (() -> Unit)? = null, actionDescription: String = "编辑") {
    Box(Modifier.fillMaxWidth().height(62.dp).padding(horizontal=8.dp)) {
        IconButton(onClick=onBack,modifier=Modifier.align(Alignment.CenterStart)) {
            JournalIcon(JournalSymbol.BACK,"返回",Modifier.size(24.dp))
        }
        Text(title, Modifier.align(Alignment.Center).padding(horizontal=48.dp),
            style=MaterialTheme.typography.titleLarge,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(onAction!=null) IconButton(onClick=onAction,modifier=Modifier.align(Alignment.CenterEnd)) {
            JournalIcon(JournalSymbol.EDIT,actionDescription,Modifier.size(25.dp))
        }
    }
}

@Composable
fun JournalStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment=Alignment.CenterHorizontally) {
        Text(value,fontSize=25.sp,lineHeight=32.sp,fontWeight=FontWeight.Medium)
        Text(label,style=MaterialTheme.typography.bodySmall,textAlign=TextAlign.Center)
    }
}

@Composable
fun SettingsRow(title: String, subtitle: String? = null, symbol: JournalSymbol, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=16.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(33.dp).clip(RoundedCornerShape(10.dp)).background(color)
            .border(1.dp,JournalColors.Ink.copy(alpha=.17f),RoundedCornerShape(10.dp)), contentAlignment=Alignment.Center) {
            JournalIcon(symbol,modifier=Modifier.size(22.dp),color=Color.White)
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(title,fontSize=17.sp,fontWeight=FontWeight.Normal)
            if(subtitle!=null) Text(subtitle,style=MaterialTheme.typography.bodySmall,color=JournalColors.Muted)
        }
        JournalIcon(JournalSymbol.NEXT,modifier=Modifier.size(18.dp),color=JournalColors.Muted.copy(alpha=.6f))
    }
}
