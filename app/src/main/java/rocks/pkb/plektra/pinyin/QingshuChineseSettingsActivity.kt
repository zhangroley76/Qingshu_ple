package rocks.pkb.plektra.pinyin

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import rocks.pkb.plektra.LocalizedComponentActivity
import rocks.pkb.plektra.ui.theme.PlektraTheme

/** Qingshu's Chinese settings: traditional output and the phrase list. */
class QingshuChineseSettingsActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlektraTheme {
                ChineseSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChineseSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var traditional by remember { mutableStateOf(QingshuChineseSettings.traditional(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("中文") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("输出字形")
            val choices = listOf(
                QingshuChineseSettings.TRADITIONAL_OFF to "简体",
                QingshuChineseSettings.TRADITIONAL_TAIWAN to "繁体（台湾字形）",
                QingshuChineseSettings.TRADITIONAL_HONGKONG to "繁体（香港字形）",
                QingshuChineseSettings.TRADITIONAL_STANDARD to "繁体（通用字形）"
            )
            for ((value, label) in choices) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = traditional == value,
                            role = Role.RadioButton,
                            onClick = {
                                traditional = value
                                QingshuChineseSettings.setTraditional(context, value)
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = traditional == value, onClick = null)
                    Text(label, modifier = Modifier.padding(start = 12.dp))
                }
            }
            Text(
                "只换字形、不换用语（软件 → 軟件）。候选、上屏与联想都用这个字形；学习的资料仍以简体保存，切回简体时不受影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { context.startActivity(Intent(context, QingshuPhrasesActivity::class.java)) }
                    .padding(16.dp)
            ) {
                Text("常用语", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "打代码出常用语，例如 yx 出邮箱",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}
