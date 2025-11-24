package com.example.ocrodds.ui

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ocrodds.Utils
import com.example.ocrodds.Utils.findColumnDuplicates
import com.example.ocrodds.Utils.findDecreasingIndices
import com.example.ocrodds.data.OcrOddsDataProvider
import com.example.ocrodds.model.OddsType
import com.example.ocrodds.ui.theme.OcrOddsTheme
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
@Composable
fun ResultScreen(
    modifier: Modifier = Modifier,
    onSend: () -> Unit = {},
    onReset: () -> Unit = {},
    onRacedayChange: (String) -> Unit = {},
    onRacenumChange: (String) -> Unit = {},
    onSiteChange: (String) -> Unit = {},
    onCellValueChange: (Int, Int, String) -> Unit = { _, _, _ -> },
    onAddData: () -> Unit = {},
    onShowButtonBar: (Int) -> Unit = { _ -> },
    onDeleteOdds: (Int) -> Unit = { _ -> },
    onInsertOdds: (Int) -> Unit = { _ -> },
    onOddsTypeChange: (String) -> Unit = { _ -> },
    onNavBarIndexChange: (Int) -> Unit = { _ -> },
    tableHeader: List<String>,
    uiState: OcrOddsUiState,
    imageUiState: ImageUiState
) {
    var openSendDialog by remember { mutableStateOf(false) }
    var openResetDialog by remember { mutableStateOf(false) }

    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            val options = OddsType.entries.map { it.name } + listOf("IMAGE")
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                ) {
                    options.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = options.size
                            ),
                            onClick = {
                                onNavBarIndexChange(index)
                                if(index < 2) {
                                    onOddsTypeChange(label)
                                }
                            },
                            selected = index == uiState.navBarIndex,
                            label = { Text(label) }
                        )
                    }
                }
            }

        },
        bottomBar = {
            if(!imeVisible) {
                ResultScreenActionButtonBar(
                    modifier = Modifier.padding(top = 5.dp),
                    onReset = {
                        openResetDialog = true
                    },
                    onSend = {
                        openSendDialog = true
                    }
                )
            }

        },
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) { paddingValues ->
        when(uiState.navBarIndex) {
            0, 1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxHeight() // Constrain height
                        .padding(paddingValues)
                        .clickable(
                            // This ensures clicks outside the TextField clear focus
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            focusManager.clearFocus()
                        }
                ) {
                    OutlinedTextField(
                        value = uiState.odds.raceday,
                        onValueChange = { newText ->
                            val digitsOnly = newText.filter { it.isDigit() }.take(8)
                            onRacedayChange(digitsOnly)
                        },
                        label = { Text("Enter Date (yyyy/MM/dd)") },
                        visualTransformation = DateVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row {
                        OptionMenu(
                            name = "SITE",
                            value = uiState.odds.sitecode,
                            options = OcrOddsDataProvider.getSites(),
                            onOptionChange = onSiteChange,
                            modifier = Modifier.weight(1f).fillMaxWidth().height(56.dp)
                        )
                        Spacer(
                            modifier = Modifier.width(5.dp)
                        )
                        OptionMenu(
                            name = "RACENUM",
                            value = uiState.odds.race,
                            options = OcrOddsDataProvider.getRacenums(),
                            onOptionChange = onRacenumChange,
                            modifier = Modifier.weight(1f).fillMaxWidth().height(56.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OddsTable(
                        uiState = uiState,
                        tableHeader = tableHeader,
                        onCellValueChange = onCellValueChange,
                        onAddData = onAddData,
                        onShowButtonBar = onShowButtonBar,
                        onDeleteOdds = onDeleteOdds,
                        onInsertOdds = onInsertOdds
                    )

                }
            }
            2 -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    imageUiState.processedBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Processed Image with Circles",
                            //modifier = Modifier.size(300.dp)
                        )
                    } ?: Text("No image processed yet")
                }

            }
        }

        if (openSendDialog) {
            val prettyJson = Json { // this returns the JsonBuilder
                prettyPrint = true
                // optional: specify indent
                prettyPrintIndent = " "
            }

            val oddsJsonString = prettyJson.encodeToString(uiState.odds)

            val scrollState = rememberScrollState()

            AlertDialog(
                onDismissRequest = { openSendDialog = false },
                title = { Text("Send Data") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()
                    ) {
                        Text(oddsJsonString)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        openSendDialog = false
                        onSend()
                    }) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { openSendDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (openResetDialog) {
            val scrollState = rememberScrollState()

            AlertDialog(
                onDismissRequest = { openResetDialog = false },
                title = { Text("Reset Data") },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()
                    ) {
                        Text("If confirmed, all data will be discarded.")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        openResetDialog = false
                        onReset()
                    }) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { openResetDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionMenu(
    modifier: Modifier = Modifier,
    name: String,
    value: String,
    options: List<String>,
    onOptionChange: (String) -> Unit = { }
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            value = value,
            onValueChange = { onOptionChange(it) },
            readOnly = true,
            label = {
                Text(
                    text = name,
                    color = Color(0xFF253172),
                    fontWeight = FontWeight.Bold
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = TextStyle(fontSize = 12.sp)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        onOptionChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
@Composable
fun OddsTable(
    modifier: Modifier = Modifier,
    tableHeader: List<String>,
    uiState: OcrOddsUiState,
    onCellValueChange: (Int, Int, String) -> Unit = { _, _, _ -> },
    onAddData: () -> Unit = {},
    onShowButtonBar: (Int) -> Unit = { _ -> },
    onDeleteOdds: (Int) -> Unit = { _ -> },
    onInsertOdds: (Int) -> Unit = { _ -> }
) {
    val oddsData = when(uiState.oddsType) {
        OddsType.WINPLACE -> uiState.odds.data
        OddsType.QUINELLA -> uiState.odds.qData
    }
    val trackTake = uiState.trackTake
    val oddsType = uiState.oddsType
    val buttonBarVisibleIndex = uiState.oddsTableButtonBarVisibleIndex

    val header = listOf("") + tableHeader + listOf("")

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF253172)),
        ) {
            header.forEachIndexed { i, cell ->
                Text(
                    text = cell,
                    modifier = Modifier
                        .weight(if(i == header.size - 1 || i == 0) 1f else 2f)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    textAlign = TextAlign.Left,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            val duplicatesTabIndices = findColumnDuplicates(oddsData, col=0)
            val duplicatesOddsIndices = findColumnDuplicates(oddsData, col=1)
            val descendingOddsIndices = findDecreasingIndices(oddsData, col=1)
            oddsData.forEachIndexed { i, row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { j, cell ->
                        if(j == 0) {
                            Column(
                                modifier = Modifier.weight(1f).height(45.dp).background(Color(0xFFB5C3FD))
                                    .drawBehind {
                                        val strokeWidth = 1.dp.toPx()
                                        val width = size.width
                                        val height = size.height

                                        // Bottom border
                                        drawLine(
                                            color = Color.Black,
                                            start = Offset(0f, height - strokeWidth / 2),
                                            end = Offset(width, height - strokeWidth / 2),
                                            strokeWidth = strokeWidth
                                        )
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    fontSize = 12.sp,
                                    text = "${i + 1}"
                                )
                            }

                        }
                        OddsTableTextField(
                            modifier = Modifier
                                .weight(2f)
                                .height(45.dp),
                            cell = cell,
                            isReadOnly = j > 1,
                            onValueChange = {
                                onCellValueChange(i, j, it)
                            },
                            isTab = j % 2 ==  0,
                            isInvalid = when(j) {
                                0 -> when(oddsType) {
                                    OddsType.WINPLACE -> cell.isEmpty() || (cell.toIntOrNull() ?: 0) !in 1..20 || i in duplicatesTabIndices
                                    OddsType.QUINELLA -> cell.isEmpty() || i in duplicatesTabIndices || !cell.contains("+") || !Utils.isValidCombo(cell)
                                }
                                1 -> when(oddsType) {
                                    OddsType.WINPLACE, OddsType.QUINELLA -> cell.isEmpty() || !cell.contains(".") || i in descendingOddsIndices || cell.contains("\n")
                                }
                                else -> false
                            },
                            isCaution = when(j) {
                                1 -> when(oddsType) {
                                    OddsType.WINPLACE, OddsType.QUINELLA -> i in duplicatesOddsIndices
                                }
                                else -> false
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .height(45.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize() // Fill all available height
                                .clickable {
                                    onShowButtonBar(i)
                                }
                                .background(Color(0xFF020E48))
                                .drawBehind {
                                    val strokeWidth = 1.dp.toPx()
                                    val width = size.width
                                    val height = size.height

                                    // Bottom border
                                    drawLine(
                                        color = Color.Black,
                                        start = Offset(0f, height - strokeWidth / 2),
                                        end = Offset(width, height - strokeWidth / 2),
                                        strokeWidth = strokeWidth
                                    )
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                tint = Color.Gray,
                                contentDescription = "Favorite icon"
                            )
                        }
                    }

                }
                AnimatedVisibility(
                    visible = i == buttonBarVisibleIndex,
                    enter = expandVertically(
                        initialHeight = { 0 }, // Start from zero height
                        clip = true // Clip content during animation
                    ),
                    exit = shrinkVertically(
                        targetHeight = { 0 } // Shrink to zero height
                    )
                ) {
                    OddsTableRowActionBar(
                        rowIndex = i,
                        onDeleteOdds = onDeleteOdds,
                        onInsertOdds = onInsertOdds
                    )
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row() {
                val textColor: Color = if(trackTake.toDoubleOrNull() != null) {
                    val isTrackTakeInValidRange = when(uiState.oddsType) {
                        OddsType.WINPLACE -> trackTake.toDouble() in 19.0..21.0
                        OddsType.QUINELLA -> trackTake.toDouble() in 26.0..28.0
                    }
                    if(isTrackTakeInValidRange) Color.Black else Color(0xFFFC1D3C)
                } else {
                    Color(0xFFFC1D3C)
                }

                Text(
                    text = "Track take: ",
                    fontSize = 12.sp
                )
                Text(
                    text = "${trackTake}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Button(
                    modifier = Modifier.wrapContentSize(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF253172)),
                    onClick = { onAddData() }
                ) {
                    Row(
                        modifier = Modifier.padding(0.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            tint = Color.Gray,
                            contentDescription = "Favorite icon"
                        )
                        Text(
                            text = "Add data"
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun OddsTableTextField(
    modifier: Modifier,
    cell: String,
    isReadOnly: Boolean,
    onValueChange: (String) -> Unit,
    isTab: Boolean,
    isInvalid: Boolean,
    isCaution: Boolean
) {
    TextField(
        value = cell,
        readOnly = isReadOnly,
        onValueChange = {
            onValueChange(it)
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = if (isTab) {
                if (isInvalid) {
                    Color(0xFFFC1D3C)
                } else {
                    Color(0xFFEBEFFF)
                }
            } else {
                if (isInvalid) {
                    Color(0xFFFC1D3C)
                } else if(isCaution) {
                    Color(0xFFF8F81F)
                } else {
                    Color.White
                }
            }
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(fontSize = 12.sp),
        shape = RoundedCornerShape(0.dp),
        modifier = modifier
    )
}

@Composable
fun OddsTableRowActionBar(
    modifier: Modifier = Modifier,
    rowIndex: Int,
    onDeleteOdds: (Int) -> Unit = { _ -> },
    onInsertOdds: (Int) -> Unit = { _ -> }
) {
    Row(
        modifier = modifier
            .background(Color(0xFF020E48))
            .fillMaxWidth()
            .height(35.dp)
            .padding(end = 30.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(
            onClick = { onDeleteOdds(rowIndex) },
            modifier = modifier,
            enabled = true,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.Gray,
                disabledContentColor = Color.Gray
            )
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Favorite"
            )
        }

        IconButton(
            onClick = { onInsertOdds(rowIndex) },
            modifier = Modifier,
            enabled = true,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.Gray,
                disabledContentColor = Color.Gray
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ){
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Favorite"
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Favorite"
                )
            }

        }

        IconButton(
            onClick = { onInsertOdds(rowIndex + 1) },
            modifier = modifier,
            enabled = true,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = Color.Gray,
                disabledContentColor = Color.Gray
            )
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Favorite"
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Favorite"
                )
            }
        }
    }
}

@Composable
fun ResultScreenActionButtonBar(
    modifier: Modifier = Modifier,
    onReset: () -> Unit = {},
    onSend: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(
            onClick = { onSend() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF253172))
        ) {
            Text("Send")
        }
        OutlinedButton(
            onClick = { onReset() }
        ) {
            Text("Reset")
        }
    }
}

class DateVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val trimmed = text.text.take(8) // Limit to yyyyMMdd
        val formatted = buildString {
            trimmed.forEachIndexed { index, char ->
                append(char)
                if (index == 3) append('/')
                if (index == 5) append('/')
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                // Map cursor position from original to formatted text
                return when {
                    offset <= 4 -> offset
                    offset <= 6 -> offset + 1
                    else -> offset + 2
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                // Map cursor position from formatted to original text
                return when {
                    offset <= 4 -> offset
                    offset <= 7 -> offset - 1
                    else -> offset - 2
                }
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}


@Preview(showBackground = true)
@Composable
fun ResultScreenPreview() {
    OcrOddsTheme {
        ResultScreen(
            modifier = Modifier.padding(0.dp),
            imageUiState = ImageUiState(),
            tableHeader = OcrOddsDataProvider.getOddsTableHeader(),
            uiState = OcrOddsUiState(
                odds = OcrOddsDataProvider.defaultOdds()
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ResultTablePreview() {
    OcrOddsTheme {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            OddsTable(
                tableHeader = OcrOddsDataProvider.getOddsTableHeader(),
                uiState = OcrOddsUiState(
                    odds = OcrOddsDataProvider.defaultOdds(),
                    oddsType = OddsType.WINPLACE,
                    oddsTableButtonBarVisibleIndex = 2
                )
            )
        }

    }
}

@Preview(showBackground = true)
@Composable
fun QOddsTablePreview() {
    OcrOddsTheme {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            OddsTable(
                tableHeader = OcrOddsDataProvider.getQOddsTableHeader(),
                uiState = OcrOddsUiState(
                    odds = OcrOddsDataProvider.defaultQOdds(),
                    oddsType = OddsType.QUINELLA,
                    oddsTableButtonBarVisibleIndex = 5
                )
            )
        }

    }
}