import 'dart:ui';
import 'package:flutter/material.dart';
import '../../../core/theme/colors.dart';
import '../../../core/theme/liquid_glass.dart';
export '../../../core/theme/liquid_glass.dart';

/// Specular rim painter for physically realistic light reflection on glass borders.
class SpecularBorderPainter extends CustomPainter {
  final double cornerRadius;
  final double borderAlpha;
  final double strokeWidth;

  const SpecularBorderPainter({
    required this.cornerRadius,
    required this.borderAlpha,
    this.strokeWidth = 1.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    final rrect = RRect.fromRectAndRadius(rect, Radius.circular(cornerRadius));

    final paint = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..shader = LiquidGlassTokens.specularBorder(alpha: borderAlpha).createShader(rect);

    canvas.drawRRect(rrect, paint);
  }

  @override
  bool shouldRepaint(covariant SpecularBorderPainter oldDelegate) {
    return oldDelegate.cornerRadius != cornerRadius ||
        oldDelegate.borderAlpha != borderAlpha ||
        oldDelegate.strokeWidth != strokeWidth;
  }
}

// =========================================================================
// 1. GlassSurface — The fundamental liquid glass container primitive
// =========================================================================
class GlassSurface extends StatelessWidget {
  final Widget child;
  final GlassPreset preset;
  final BorderRadius? borderRadius;
  final ShapeBorder? shape;
  final double? cornerRadius;
  final double? borderAlpha;
  final EdgeInsetsGeometry? margin;
  final EdgeInsetsGeometry? padding;
  final VoidCallback? onTap;
  final bool hasGlow;
  final Color glowColor;

  const GlassSurface({
    super.key,
    required this.child,
    this.preset = GlassPreset.dark,
    this.borderRadius,
    this.shape,
    this.cornerRadius,
    this.borderAlpha,
    this.margin,
    this.padding,
    this.onTap,
    this.hasGlow = false,
    this.glowColor = LofiQColors.brandTangerine,
  });

  @override
  Widget build(BuildContext context) {
    final effectiveRadius = cornerRadius ?? preset.defaultCornerRadius;
    BorderRadius rRadius;
    if (borderRadius != null) {
      rRadius = borderRadius!;
    } else if (shape is RoundedRectangleBorder) {
      final rrb = shape as RoundedRectangleBorder;
      if (rrb.borderRadius is BorderRadius) {
        rRadius = rrb.borderRadius as BorderRadius;
      } else {
        rRadius = BorderRadius.circular(effectiveRadius);
      }
    } else {
      rRadius = BorderRadius.circular(effectiveRadius);
    }
    final effectiveBorderAlpha = borderAlpha ?? preset.borderAlpha;

    Widget innerContent = child;
    if (padding != null) {
      innerContent = Padding(padding: padding!, child: innerContent);
    }

    Widget surface = Container(
      margin: margin,
      decoration: BoxDecoration(
        borderRadius: rRadius,
        boxShadow: preset.elevation > 0
            ? [
                BoxShadow(
                  color: hasGlow ? glowColor.withValues(alpha: 0.35) : Colors.black.withValues(alpha: 0.55),
                  blurRadius: preset.elevation * 1.5,
                  spreadRadius: 0,
                  offset: Offset(0, preset.elevation * 0.4),
                ),
              ]
            : null,
      ),
      child: ClipRRect(
        borderRadius: rRadius,
        child: BackdropFilter(
          filter: ImageFilter.blur(
            sigmaX: preset.blurSigma,
            sigmaY: preset.blurSigma,
          ),
          child: CustomPaint(
            foregroundPainter: SpecularBorderPainter(
              cornerRadius: effectiveRadius,
              borderAlpha: effectiveBorderAlpha,
            ),
            child: Container(
              decoration: BoxDecoration(
                borderRadius: rRadius,
                gradient: LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Colors.white.withValues(alpha: 0.08),
                    preset.baseSubstrate.withValues(alpha: preset.bgAlpha),
                  ],
                ),
              ),
              child: innerContent,
            ),
          ),
        ),
      ),
    );

    if (onTap != null) {
      return InkWell(
        onTap: onTap,
        borderRadius: rRadius,
        hoverColor: Colors.white.withValues(alpha: 0.06),
        splashColor: LofiQColors.brandTangerine.withValues(alpha: 0.15),
        child: surface,
      );
    }

    return surface;
  }
}

// =========================================================================
// 2. GlassCard — Standard content and setting card container
// =========================================================================
class GlassCard extends StatelessWidget {
  final Widget child;
  final GlassPreset preset;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final BorderRadius? borderRadius;
  final double? cornerRadius;
  final double? borderAlpha;
  final VoidCallback? onTap;
  final bool hasGlow;
  final Color glowColor;

  const GlassCard({
    super.key,
    required this.child,
    this.preset = GlassPreset.dark,
    this.padding = const EdgeInsets.all(16.0),
    this.margin,
    this.borderRadius,
    this.cornerRadius,
    this.borderAlpha,
    this.onTap,
    this.hasGlow = false,
    this.glowColor = LofiQColors.brandTangerine,
  });

  @override
  Widget build(BuildContext context) {
    return GlassSurface(
      preset: preset,
      margin: margin,
      borderRadius: borderRadius,
      cornerRadius: cornerRadius,
      borderAlpha: borderAlpha,
      onTap: onTap,
      hasGlow: hasGlow,
      glowColor: glowColor,
      child: Padding(
        padding: padding ?? const EdgeInsets.all(16.0),
        child: child,
      ),
    );
  }
}

// =========================================================================
// 3. GlassPanel — Large grouped section container with header slot
// =========================================================================
class GlassPanel extends StatelessWidget {
  final Widget child;
  final String? title;
  final String? subtitle;
  final IconData? icon;
  final Widget? action;
  final GlassPreset preset;
  final EdgeInsetsGeometry? padding;
  final EdgeInsetsGeometry? margin;
  final double cornerRadius;

  const GlassPanel({
    super.key,
    required this.child,
    this.title,
    this.subtitle,
    this.icon,
    this.action,
    this.preset = GlassPreset.dark,
    this.padding = const EdgeInsets.all(20.0),
    this.margin,
    this.cornerRadius = 24.0,
  });

  @override
  Widget build(BuildContext context) {
    return GlassCard(
      preset: preset,
      cornerRadius: cornerRadius,
      padding: padding,
      margin: margin,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (title != null || icon != null || action != null) ...[
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    if (icon != null) ...[
                      Container(
                        padding: const EdgeInsets.all(8.0),
                        decoration: BoxDecoration(
                          color: LofiQColors.brandTangerine.withValues(alpha: 0.16),
                          borderRadius: BorderRadius.circular(10.0),
                          border: Border.all(
                            color: LofiQColors.brandTangerine.withValues(alpha: 0.35),
                            width: 1.0,
                          ),
                        ),
                        child: Icon(icon, color: LofiQColors.brandTangerine, size: 20),
                      ),
                      const SizedBox(width: 12.0),
                    ],
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        if (title != null)
                          Text(
                            title!,
                            style: const TextStyle(
                              fontSize: 16.0,
                              fontWeight: FontWeight.bold,
                              color: LofiQColors.textPrimary,
                              letterSpacing: -0.2,
                            ),
                          ),
                        if (subtitle != null) ...[
                          const SizedBox(height: 2.0),
                          Text(
                            subtitle!,
                            style: const TextStyle(
                              fontSize: 12.0,
                              color: LofiQColors.textSecondary,
                            ),
                          ),
                        ],
                      ],
                    ),
                  ],
                ),
                ?action,
              ],
            ),
            const SizedBox(height: 18.0),
          ],
          child,
        ],
      ),
    );
  }
}

// =========================================================================
// 4. GlassButton — Interactive button with tactile press animation
// =========================================================================
class GlassButton extends StatefulWidget {
  final VoidCallback? onPressed;
  final Widget child;
  final bool isPrimary;
  final Color accentColor;
  final GlassPreset preset;
  final double cornerRadius;
  final EdgeInsetsGeometry padding;

  const GlassButton({
    super.key,
    required this.onPressed,
    required this.child,
    this.isPrimary = false,
    this.accentColor = LofiQColors.brandTangerine,
    this.preset = GlassPreset.light,
    this.cornerRadius = 16.0,
    this.padding = const EdgeInsets.symmetric(horizontal: 18.0, vertical: 12.0),
  });

  @override
  State<GlassButton> createState() => _GlassButtonState();
}

class _GlassButtonState extends State<GlassButton> {
  bool _isHovered = false;
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final scale = _isPressed ? 0.96 : (_isHovered ? 1.02 : 1.0);
    final effectiveRadius = BorderRadius.circular(widget.cornerRadius);

    return AnimatedScale(
      scale: scale,
      duration: const Duration(milliseconds: 120),
      curve: Curves.easeOutCubic,
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovered = true),
        onExit: (_) => setState(() => _isHovered = false),
        child: GestureDetector(
          onTapDown: (_) => setState(() => _isPressed = true),
          onTapUp: (_) => setState(() => _isPressed = false),
          onTapCancel: () => setState(() => _isPressed = false),
          onTap: widget.onPressed,
          child: Container(
            decoration: BoxDecoration(
              borderRadius: effectiveRadius,
              boxShadow: widget.isPrimary
                  ? [
                      BoxShadow(
                        color: widget.accentColor.withValues(alpha: 0.45),
                        blurRadius: 12.0,
                        offset: const Offset(0, 3),
                      ),
                    ]
                  : null,
            ),
            child: ClipRRect(
              borderRadius: effectiveRadius,
              child: BackdropFilter(
                filter: ImageFilter.blur(
                  sigmaX: widget.preset.blurSigma,
                  sigmaY: widget.preset.blurSigma,
                ),
                child: CustomPaint(
                  foregroundPainter: SpecularBorderPainter(
                    cornerRadius: widget.cornerRadius,
                    borderAlpha: widget.isPrimary ? 0.45 : widget.preset.borderAlpha,
                  ),
                  child: Container(
                    padding: widget.padding,
                    decoration: BoxDecoration(
                      borderRadius: effectiveRadius,
                      color: widget.isPrimary
                          ? widget.accentColor.withValues(alpha: _isHovered ? 0.95 : 0.85)
                          : widget.preset.baseSubstrate.withValues(alpha: _isHovered ? 0.55 : widget.preset.bgAlpha),
                    ),
                    child: widget.child,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

// =========================================================================
// 5. GlassIconButton — Circular or squircle icon button with tactile feedback
// =========================================================================
class GlassIconButton extends StatefulWidget {
  final VoidCallback? onPressed;
  final IconData icon;
  final String? tooltip;
  final double size;
  final double iconSize;
  final Color tint;
  final Color? iconColor;
  final double cornerRadius;
  final GlassPreset preset;

  const GlassIconButton({
    super.key,
    required this.onPressed,
    required this.icon,
    this.tooltip,
    this.size = 40.0,
    this.iconSize = 20.0,
    this.tint = LofiQColors.textPrimary,
    this.iconColor,
    this.cornerRadius = 999.0,
    this.preset = GlassPreset.subtle,
  });

  @override
  State<GlassIconButton> createState() => _GlassIconButtonState();
}

class _GlassIconButtonState extends State<GlassIconButton> {
  bool _isHovered = false;
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final scale = _isPressed ? 0.92 : (_isHovered ? 1.08 : 1.0);
    final effectiveIconColor = widget.iconColor ?? widget.tint;

    Widget btn = AnimatedScale(
      scale: scale,
      duration: const Duration(milliseconds: 120),
      child: MouseRegion(
        onEnter: (_) => setState(() => _isHovered = true),
        onExit: (_) => setState(() => _isHovered = false),
        child: GestureDetector(
          onTapDown: (_) => setState(() => _isPressed = true),
          onTapUp: (_) => setState(() => _isPressed = false),
          onTapCancel: () => setState(() => _isPressed = false),
          onTap: widget.onPressed,
          child: SizedBox(
            width: widget.size,
            height: widget.size,
            child: GlassSurface(
              preset: widget.preset,
              cornerRadius: widget.cornerRadius,
              child: Center(
                child: Icon(
                  widget.icon,
                  size: widget.iconSize,
                  color: _isHovered ? LofiQColors.brandTangerine : effectiveIconColor,
                ),
              ),
            ),
          ),
        ),
      ),
    );

    if (widget.tooltip != null) {
      return Tooltip(message: widget.tooltip!, child: btn);
    }
    return btn;
  }
}

// =========================================================================
// 6. GlassNavigationSurface — Floating capsule navigation surface
// =========================================================================
class GlassNavigationSurface extends StatelessWidget {
  final Widget child;
  final double cornerRadius;
  final EdgeInsetsGeometry? margin;

  const GlassNavigationSurface({
    super.key,
    required this.child,
    this.cornerRadius = 24.0,
    this.margin,
  });

  @override
  Widget build(BuildContext context) {
    return GlassSurface(
      preset: GlassPreset.floating,
      cornerRadius: cornerRadius,
      borderAlpha: 0.38,
      margin: margin,
      hasGlow: true,
      glowColor: Colors.black,
      child: child,
    );
  }
}

// =========================================================================
// 7. GlassDialog — Premium modal alert dialog with translucent backdrop
// =========================================================================
class GlassDialog extends StatelessWidget {
  final String? title;
  final IconData? icon;
  final Widget content;
  final Widget? confirmButton;
  final Widget? dismissButton;
  final List<Widget>? actions;
  final double cornerRadius;
  final double maxWidth;

  const GlassDialog({
    super.key,
    this.title,
    this.icon,
    required this.content,
    this.confirmButton,
    this.dismissButton,
    this.actions,
    this.cornerRadius = 28.0,
    this.maxWidth = 440.0,
  });

  static Future<T?> show<T>({
    required BuildContext context,
    required WidgetBuilder builder,
    bool barrierDismissible = true,
  }) {
    return showDialog<T>(
      context: context,
      barrierDismissible: barrierDismissible,
      barrierColor: Colors.black.withValues(alpha: 0.65),
      builder: builder,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 24.0),
      child: ConstrainedBox(
        constraints: BoxConstraints(maxWidth: maxWidth),
        child: GlassSurface(
          preset: GlassPreset.modal,
          cornerRadius: cornerRadius,
          borderAlpha: 0.42,
          hasGlow: true,
          glowColor: Colors.black,
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                if (icon != null) ...[
                  Center(
                    child: Container(
                      width: 52,
                      height: 52,
                      decoration: BoxDecoration(
                        color: LofiQColors.brandTangerine.withValues(alpha: 0.16),
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: LofiQColors.brandTangerine.withValues(alpha: 0.35),
                          width: 1.0,
                        ),
                      ),
                      child: Center(
                        child: Icon(icon, color: LofiQColors.brandTangerine, size: 26),
                      ),
                    ),
                  ),
                  const SizedBox(height: 18.0),
                ],
                if (title != null) ...[
                  Text(
                    title!,
                    style: const TextStyle(
                      fontSize: 18.0,
                      fontWeight: FontWeight.bold,
                      color: LofiQColors.textPrimary,
                    ),
                  ),
                  const SizedBox(height: 14.0),
                ],
                content,
                if (actions != null && actions!.isNotEmpty) ...[
                  const SizedBox(height: 24.0),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      for (int i = 0; i < actions!.length; i++) ...[
                        if (i > 0) const SizedBox(width: 12.0),
                        actions![i],
                      ],
                    ],
                  ),
                ] else if (confirmButton != null || dismissButton != null) ...[
                  const SizedBox(height: 24.0),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.end,
                    children: [
                      if (dismissButton != null) ...[
                        dismissButton!,
                        const SizedBox(width: 12.0),
                      ],
                      if (confirmButton != null) confirmButton!,
                    ],
                  ),
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

// =========================================================================
// 8. GlassModalSheet — Modal bottom sheet container with specular top rim
// =========================================================================
class GlassModalSheet extends StatelessWidget {
  final Widget child;
  final double cornerRadius;

  const GlassModalSheet({
    super.key,
    required this.child,
    this.cornerRadius = 28.0,
  });

  @override
  Widget build(BuildContext context) {
    return GlassSurface(
      preset: GlassPreset.modal,
      borderRadius: BorderRadius.vertical(top: Radius.circular(cornerRadius)),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            margin: const EdgeInsets.symmetric(vertical: 12.0),
            width: 44.0,
            height: 4.0,
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.35),
              borderRadius: BorderRadius.circular(2.0),
            ),
          ),
          child,
        ],
      ),
    );
  }
}

// =========================================================================
// 9. GlassSegmentedControl — Multi-segment selector with sliding glass pill
// =========================================================================
class GlassSegmentedControl extends StatelessWidget {
  final List<String> items;
  final int selectedIndex;
  final ValueChanged<int> onSelect;

  const GlassSegmentedControl({
    super.key,
    required this.items,
    required this.selectedIndex,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 44.0,
      padding: const EdgeInsets.all(4.0),
      decoration: BoxDecoration(
        color: GlassPreset.subtle.baseSubstrate.withValues(alpha: 0.40),
        borderRadius: BorderRadius.circular(14.0),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.12),
          width: 1.0,
        ),
      ),
      child: Row(
        children: items.asMap().entries.map((entry) {
          final idx = entry.key;
          final title = entry.value;
          final isSelected = idx == selectedIndex;

          return Expanded(
            child: GestureDetector(
              onTap: () => onSelect(idx),
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 180),
                curve: Curves.easeOutCubic,
                decoration: BoxDecoration(
                  color: isSelected ? Colors.white.withValues(alpha: 0.18) : Colors.transparent,
                  borderRadius: BorderRadius.circular(10.0),
                  border: isSelected
                      ? Border.all(color: Colors.white.withValues(alpha: 0.35), width: 1.0)
                      : null,
                ),
                child: Center(
                  child: Text(
                    title,
                    style: TextStyle(
                      fontSize: 13.0,
                      fontWeight: isSelected ? FontWeight.bold : FontWeight.w500,
                      color: isSelected ? Colors.white : LofiQColors.textSecondary,
                    ),
                  ),
                ),
              ),
            ),
          );
        }).toList(),
      ),
    );
  }
}

// =========================================================================
// 10. GlassMenu — Floating contextual dropdown menu surface
// =========================================================================
class GlassMenu<T> extends StatelessWidget {
  final List<PopupMenuEntry<T>> items;
  final Widget child;
  final PopupMenuItemSelected<T>? onSelected;

  const GlassMenu({
    super.key,
    required this.items,
    required this.child,
    this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<T>(
      color: GlassPreset.elevated.baseSubstrate.withValues(alpha: 0.94),
      elevation: 12.0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16.0),
        side: BorderSide(
          color: Colors.white.withValues(alpha: 0.25),
          width: 1.0,
        ),
      ),
      onSelected: onSelected,
      itemBuilder: (context) => items,
      child: child,
    );
  }
}

// =========================================================================
// 11. GlassTooltip — Compact floating pill tooltip
// =========================================================================
class GlassTooltip extends StatelessWidget {
  final String message;
  final Widget child;

  const GlassTooltip({
    super.key,
    required this.message,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: message,
      decoration: BoxDecoration(
        color: GlassPreset.subtle.baseSubstrate.withValues(alpha: 0.85),
        borderRadius: BorderRadius.circular(12.0),
        border: Border.all(
          color: Colors.white.withValues(alpha: 0.25),
          width: 1.0,
        ),
      ),
      textStyle: const TextStyle(
        fontSize: 11.0,
        color: LofiQColors.textPrimary,
        fontWeight: FontWeight.w500,
      ),
      child: child,
    );
  }
}

// =========================================================================
// 12. GlassInput — Translucent text input field with focused specular border
// =========================================================================
class GlassInput extends StatefulWidget {
  final TextEditingController? controller;
  final String hintText;
  final IconData? prefixIcon;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final bool autofocus;
  final double cornerRadius;
  final Widget? suffixIcon;

  const GlassInput({
    super.key,
    this.controller,
    this.hintText = 'Search...',
    this.prefixIcon,
    this.onChanged,
    this.onSubmitted,
    this.autofocus = false,
    this.cornerRadius = 16.0,
    this.suffixIcon,
  });

  @override
  State<GlassInput> createState() => _GlassInputState();
}

class _GlassInputState extends State<GlassInput> {
  final FocusNode _focusNode = FocusNode();
  TextEditingController? _internalController;
  bool _isFocused = false;

  TextEditingController get _effectiveController =>
      widget.controller ?? (_internalController ??= TextEditingController());

  @override
  void initState() {
    super.initState();
    _focusNode.addListener(() {
      setState(() => _isFocused = _focusNode.hasFocus);
    });
  }

  @override
  void dispose() {
    _focusNode.dispose();
    _internalController?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48.0,
      decoration: BoxDecoration(
        color: GlassPreset.subtle.baseSubstrate.withValues(alpha: _isFocused ? 0.45 : 0.30),
        borderRadius: BorderRadius.circular(widget.cornerRadius),
        border: Border.all(
          color: _isFocused
              ? LofiQColors.brandTangerine.withValues(alpha: 0.85)
              : Colors.white.withValues(alpha: 0.15),
          width: 1.2,
        ),
      ),
      child: Row(
        children: [
          if (widget.prefixIcon != null) ...[
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 14.0),
              child: Icon(
                widget.prefixIcon,
                color: _isFocused ? LofiQColors.brandTangerine : LofiQColors.textTertiary,
                size: 20.0,
              ),
            ),
          ] else
            const SizedBox(width: 14.0),
          Expanded(
            child: TextField(
              controller: _effectiveController,
              focusNode: _focusNode,
              autofocus: widget.autofocus,
              onChanged: widget.onChanged,
              onSubmitted: widget.onSubmitted,
              style: const TextStyle(color: LofiQColors.textPrimary, fontSize: 14.0),
              decoration: InputDecoration(
                hintText: widget.hintText,
                hintStyle: const TextStyle(color: LofiQColors.textTertiary, fontSize: 14.0),
                border: InputBorder.none,
                isDense: true,
              ),
            ),
          ),
          if (widget.suffixIcon != null)
            widget.suffixIcon!
          else if (_effectiveController.text.isNotEmpty)
            IconButton(
              icon: const Icon(Icons.clear_rounded, size: 18, color: LofiQColors.textTertiary),
              onPressed: () {
                _effectiveController.clear();
                if (widget.onChanged != null) widget.onChanged!('');
                setState(() {});
              },
            ),
          const SizedBox(width: 6.0),
        ],
      ),
    );
  }
}

// =========================================================================
// 13. GlassSlider — Precision audio slider with glowing Tangerine track
// =========================================================================
class GlassSlider extends StatelessWidget {
  final double value;
  final ValueChanged<double>? onChanged;
  final double min;
  final double max;
  final Color activeColor;

  const GlassSlider({
    super.key,
    required this.value,
    required this.onChanged,
    this.min = 0.0,
    this.max = 1.0,
    this.activeColor = LofiQColors.brandTangerine,
  });

  @override
  Widget build(BuildContext context) {
    return SliderTheme(
      data: SliderTheme.of(context).copyWith(
        trackHeight: 4.0,
        activeTrackColor: activeColor,
        inactiveTrackColor: Colors.white.withValues(alpha: 0.12),
        thumbColor: Colors.white,
        thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 7.0),
        overlayColor: activeColor.withValues(alpha: 0.20),
        overlayShape: const RoundSliderOverlayShape(overlayRadius: 16.0),
      ),
      child: Slider(
        value: value.clamp(min, max),
        min: min,
        max: max,
        onChanged: onChanged,
      ),
    );
  }
}

// =========================================================================
// 14. GlassSwitch — Smooth toggle switch with frosted glass pill track
// =========================================================================
class GlassSwitch extends StatelessWidget {
  final bool value;
  final ValueChanged<bool>? onChanged;
  final Color activeColor;

  const GlassSwitch({
    super.key,
    required this.value,
    required this.onChanged,
    this.activeColor = LofiQColors.brandTangerine,
  });

  @override
  Widget build(BuildContext context) {
    return Switch(
      value: value,
      onChanged: onChanged,
      activeThumbColor: Colors.white,
      activeTrackColor: activeColor,
      inactiveThumbColor: LofiQColors.textSecondary,
      inactiveTrackColor: Colors.white.withValues(alpha: 0.10),
      trackOutlineColor: WidgetStateProperty.resolveWith(
        (states) => Colors.white.withValues(alpha: 0.18),
      ),
    );
  }
}
