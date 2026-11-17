package com.mandrecode.tempo.features.onboarding.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mandrecode.tempo.R
import com.mandrecode.tempo.core.ui.components.glassCardElevation
import com.mandrecode.tempo.core.ui.glass.glassCardColor
import com.mandrecode.tempo.core.ui.theme.onboardingPageHeadlineRegular
import com.mandrecode.tempo.core.ui.theme.onboardingPageHeadlineShort

// The pages that only explain something: one hero, one headline, and a card per idea. Lifted out of
// OnboardingContent when a fourth of them arrived — that file was carrying both the shell every page
// sits in and the content of half of them, which is two subjects.

/**
 * First, because it is the screen the app opens on: what the other three tabs are for reads better
 * once you have seen where the day itself lives.
 */
@Composable
internal fun FocusPage(layout: OnboardingLayout) {
    EducationPage(
        layout = layout,
        iconRes = R.drawable.ic_focus,
        title = stringResource(R.string.onboarding_focus_title),
        description = stringResource(R.string.onboarding_focus_description),
        concepts =
            listOf(
                EducationConcept(
                    iconRes = R.drawable.ic_event,
                    title = stringResource(R.string.onboarding_focus_today_concept_title),
                    description = stringResource(R.string.onboarding_focus_today_concept_description),
                ),
                EducationConcept(
                    iconRes = R.drawable.ic_timer,
                    title = stringResource(R.string.onboarding_focus_session_concept_title),
                    description = stringResource(R.string.onboarding_focus_session_concept_description),
                ),
            ),
    )
}

@Composable
internal fun TasksAndCategoriesPage(layout: OnboardingLayout) {
    EducationPage(
        layout = layout,
        iconRes = R.drawable.ic_tasks,
        title = stringResource(R.string.onboarding_tasks_title),
        description = stringResource(R.string.onboarding_tasks_description),
        concepts =
            listOf(
                EducationConcept(
                    iconRes = R.drawable.ic_add_task,
                    title = stringResource(R.string.onboarding_tasks_concept_title),
                    description = stringResource(R.string.onboarding_tasks_concept_description),
                ),
                EducationConcept(
                    iconRes = R.drawable.ic_category,
                    title = stringResource(R.string.onboarding_categories_concept_title),
                    description = stringResource(R.string.onboarding_categories_concept_description),
                ),
            ),
    )
}

@Composable
internal fun RoutinesAndRemindersPage(layout: OnboardingLayout) {
    EducationPage(
        layout = layout,
        iconRes = R.drawable.ic_routine,
        title = stringResource(R.string.onboarding_routines_title),
        description = stringResource(R.string.onboarding_routines_description),
        concepts =
            listOf(
                EducationConcept(
                    iconRes = R.drawable.ic_repeat,
                    title = stringResource(R.string.onboarding_routines_concept_title),
                    description = stringResource(R.string.onboarding_routines_concept_description),
                ),
                EducationConcept(
                    iconRes = R.drawable.ic_reminder,
                    title = stringResource(R.string.onboarding_reminders_concept_title),
                    description = stringResource(R.string.onboarding_reminders_concept_description),
                ),
            ),
    )
}

@Composable
private fun EducationPage(
    layout: OnboardingLayout,
    iconRes: Int,
    title: String,
    description: String,
    concepts: List<EducationConcept>,
) = AdaptiveOnboardingPage(
    layout = layout,
    intro = {
        if (!layout.isShort) {
            HeroIcon(iconRes = iconRes)
        }
        Text(
            text = title,
            style =
                if (layout.isShort) {
                    MaterialTheme.typography.onboardingPageHeadlineShort
                } else {
                    MaterialTheme.typography.onboardingPageHeadlineRegular
                },
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style =
                if (layout.isShort) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    },
    body = {
        concepts.forEach { concept -> ConceptCard(concept = concept, compact = layout.isShort) }
    },
)

@Composable
private fun ConceptCard(
    concept: EducationConcept,
    compact: Boolean,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = glassCardColor(MaterialTheme.colorScheme.surfaceContainer)),
        elevation = glassCardElevation(),
        border = null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(if (compact) 12.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(concept.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (compact) 24.dp else 28.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    concept.title,
                    style =
                        if (compact) {
                            MaterialTheme.typography.titleSmall
                        } else {
                            MaterialTheme.typography.titleMedium
                        },
                )
                Text(
                    concept.description,
                    style =
                        if (compact) {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun HeroIcon(iconRes: Int) {
    Surface(
        shape = EducationIconShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier =
                Modifier
                    .padding(24.dp)
                    .size(48.dp),
        )
    }
}

private val EducationIconShape = RoundedCornerShape(28.dp)

private data class EducationConcept(
    val iconRes: Int,
    val title: String,
    val description: String,
)
