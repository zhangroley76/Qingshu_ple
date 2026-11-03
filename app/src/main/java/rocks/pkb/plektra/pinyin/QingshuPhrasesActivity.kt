package rocks.pkb.plektra.pinyin

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import rocks.pkb.plektra.LocalizedComponentActivity
import rocks.pkb.plektra.ui.theme.PlektraTheme

/**
 * Lists and edits the Chinese phrases: text plus an optional code of lowercase letters. Typing the
 * code in Chinese mode offers the phrase as a candidate (code `yx` → an email address).
 */
class QingshuPhrasesActivity : LocalizedComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlektraTheme {
                PhrasesScreen(onBack = { finish() })
            }
        }
    }
}

/** What the edit dialog is doing: adding (index null) or editing the phrase at [index]. */
private data class Editing(val index: Int?, val text: String, val code: String, val group: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhrasesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var phrases by remember { mutableStateOf(QingshuPhrases.list(context)) }
    var editing by remember { mutableStateOf<Editing?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("中文常用语") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = Editing(null, "", "", "") }) {
                Icon(Icons.Filled.Add, contentDescription = "新增")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "在中文模式打「代码」，常用语会出现在候选里。例如代码 yx、内容填邮箱，打 yx 就能选到邮箱。" +
                        "代码只能用小写英文字母。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (phrases.isEmpty()) {
                item {
                    Text(
                        "还没有常用语。点右下角的 + 新增。",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
                    )
                }
            }
            itemsIndexed(phrases) { index, phrase ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = Editing(index, phrase.text, phrase.code, phrase.group) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(phrase.text, style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                        Text(
                            if (phrase.code.isEmpty()) "（没有代码，不会出现在候选里）" else "代码：${phrase.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { phrases = QingshuPhrases.remove(context, index) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除")
                    }
                }
                HorizontalDivider()
            }
        }
    }

    editing?.let { current ->
        var text by remember(current) { mutableStateOf(current.text) }
        var code by remember(current) { mutableStateOf(current.code) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (current.index == null) "新增常用语" else "编辑常用语") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = QingshuPhrases.normalizeCode(it) },
                        label = { Text("代码（小写字母，如 yx）") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("内容") },
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        phrases = if (current.index == null) {
                            QingshuPhrases.add(context, text.trim(), code)
                        } else {
                            QingshuPhrases.update(context, current.index, text.trim(), code, current.group)
                        }
                        editing = null
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { editing = null }) { Text("取消") }
                    Spacer(Modifier.width(4.dp))
                }
            }
        )
    }
}
