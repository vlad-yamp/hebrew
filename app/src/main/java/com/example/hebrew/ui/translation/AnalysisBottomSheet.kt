package com.example.hebrew.ui.translation

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.hebrew.databinding.BottomSheetAnalysisBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AnalysisBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAnalysisBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvAnalysisTitle.text = arguments?.getString(ARG_TITLE) ?: ""
        binding.tvAnalysisContent.text = parseMarkdownBold(arguments?.getString(ARG_CONTENT) ?: "")

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun parseMarkdownBold(text: String): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val pattern = Regex("\\*\\*(.+?)\\*\\*")
        var lastEnd = 0
        for (match in pattern.findAll(text)) {
            builder.append(text.substring(lastEnd, match.range.first))
            val start = builder.length
            builder.append(match.groupValues[1])
            builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            lastEnd = match.range.last + 1
        }
        builder.append(text.substring(lastEnd))
        return builder
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_CONTENT = "content"

        fun newInstance(title: String, content: String) = AnalysisBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_TITLE, title)
                putString(ARG_CONTENT, content)
            }
        }
    }
}
