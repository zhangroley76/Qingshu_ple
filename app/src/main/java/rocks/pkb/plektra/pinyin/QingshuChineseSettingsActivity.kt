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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import rocks.pkb.plektra.LocalizedComponentActivity
import rocks.pkb.plektra.ui.theme.PlektraTheme

/** Qingshu's Chinese settings: output form, mixed Chinese/English, fuzzy pinyin and the phrase list. */
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
            SectionTitle("其他语言")
            var korean by remember { mutableStateOf(QingshuMode.MODE_KOREAN in QingshuMode.extraLanguages(context)) }
            SwitchRow(
                title = "한국어（韩文）",
                description = "两套式键位。开启后 Shift+空格依序切换：English → 中文 → 한국어",
                checked = korean
            ) {
                korean = it
                val languages = QingshuMode.extraLanguages(context)
                QingshuMode.setExtraLanguages(
                    context,
                    if (it) languages + QingshuMode.MODE_KOREAN else languages - QingshuMode.MODE_KOREAN
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            SectionTitle("中英混打")
            var english by remember { mutableStateOf(QingshuChineseSettings.englishInChinese(context)) }
            var chineseFirst by remember { mutableStateOf(QingshuChineseSettings.chineseFirst(context)) }
            SwitchRow(
                title = "拼音里也给英文单字",
                description = "中文模式打 hello、meeting 这类英文时，候选里也有英文单字，不用切换",
                checked = english
            ) {
                english = it
                QingshuChineseSettings.setEnglishInChinese(context, it)
            }
            SwitchRow(
                title = "中文候选排在英文前面",
                description = "关闭时按可能性混排（打 hello 时 hello 会在前面）",
                checked = chineseFirst,
                enabled = english
            ) {
                chineseFirst = it
                QingshuChineseSettings.setChineseFirst(context, it)
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            SectionTitle("模糊音")
            Text(
                "勾选的两个音当成一样（例如 z = zh 时，打 zongwen 也出「中文」）。简拼不用设定：只打声母就行，例如 nh 出「你好」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            var fuzzy by remember { mutableStateOf(QingshuChineseSettings.fuzzy(context)) }
            for ((rule, label) in QingshuChineseSettings.FUZZY_RULES) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = rule in fuzzy,
                            role = Role.Checkbox,
                            onValueChange = { on ->
                                fuzzy = if (on) fuzzy + rule else fuzzy - rule
                                QingshuChineseSettings.setFuzzy(context, fuzzy)
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = rule in fuzzy, onCheckedChange = null)
                    Text(label, modifier = Modifier.padding(start = 12.dp), fontFamily = FontFamily.Monospace)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            SectionTitle("词库")
            var general by remember { mutableStateOf(QingshuChineseSettings.generalDictionary(context)) }
            SwitchRow(
                title = "通用大词库",
                description = "青简的扩充词汇（约 68 MB），候选更多更全",
                checked = general
            ) {
                general = it
                QingshuChineseSettings.setGeneralDictionary(context, it)
            }
            Text(
                "专业词库：勾选后这些领域的词会出现在候选里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            var domains by remember { mutableStateOf(QingshuChineseSettings.domains(context)) }
            for ((id, label) in QingshuChineseSettings.DOMAINS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = id in domains,
                            role = Role.Checkbox,
                            onValueChange = { on ->
                                domains = if (on) domains + id else domains - id
                                QingshuChineseSettings.setDomains(context, domains)
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = id in domains, onCheckedChange = null)
                    Text(label, modifier = Modifier.padding(start = 12.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            SectionTitle("标点")
            var corner by remember { mutableStateOf(QingshuChineseSettings.cornerQuotes(context)) }
            SwitchRow(
                title = "引号用「」『』",
                description = "中文模式打 \" 和 ' 时出直角引号（台湾、香港常用），自动配对开合",
                checked = corner
            ) {
                corner = it
                QingshuChineseSettings.setCornerQuotes(context, it)
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
            SectionTitle("打字统计")
            var statsVersion by remember { mutableStateOf(0) }
            val stats = remember(statsVersion) {
                rocks.pkb.plektra.core.suggestions.TypingStats.summary(
                    context.getSharedPreferences(rocks.pkb.plektra.core.suggestions.TypingStats.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                )
            }
            val chinese = remember(statsVersion) { QingshuStats.chineseChars(context) }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text("中文：打了 $chinese 个字", style = MaterialTheme.typography.bodyMedium)
                Text("英文：打了 ${stats.words} 个字，自动纠错 ${stats.typosFixed} 次", style = MaterialTheme.typography.bodyMedium)
                stats.wordsPerMinute?.let { Text("英文速度：每分钟约 $it 个字", style = MaterialTheme.typography.bodyMedium) }
                stats.favoriteWord?.let { Text("最常打的英文字：$it", style = MaterialTheme.typography.bodyMedium) }
                Text(
                    "只记数量，不记你打的内容（最常打的英文字除外，只存在手机里）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = {
                    rocks.pkb.plektra.core.suggestions.TypingStats.reset(
                        context.getSharedPreferences(rocks.pkb.plektra.core.suggestions.TypingStats.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                    )
                    QingshuStats.reset(context)
                    statsVersion++
                }) { Text("清除统计") }
            }

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
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = null)
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
