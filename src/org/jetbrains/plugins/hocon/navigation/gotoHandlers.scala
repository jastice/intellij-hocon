package org.jetbrains.plugins.hocon.navigation

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.hocon.CommonUtil._
import org.jetbrains.plugins.hocon.psi.{HKeyedField, HPath, ResolutionCtx}

class HoconGotoDeclarationHandler extends GotoDeclarationHandler {
  def getGotoDeclarationTargets(sourceElement: PsiElement, offset: Int, editor: Editor): Array[PsiElement] = {
    sourceElement.parentOfType[HPath].fold(PsiElement.EMPTY_ARRAY) { path =>
      val resCtx = ResolutionCtx(path.getContainingFile, followIncludes = true)
      val fullPathResolved = for {
        fullPath <- path.substitution.path
        keyedField <- fullPath.resolve(reverse = true, resCtx).nextOption
      } yield (fullPath, keyedField)

      def gotoPrefix(res: Option[(HPath, HKeyedField)]): Option[HKeyedField] = res match {
        case Some((`path`, field)) => Some(field)
        case Some((subpath, field)) =>
          gotoPrefix(for (pref <- subpath.prefix; prefField <- field.prefixingField) yield (pref, prefField))
        case _ => None
      }
      gotoPrefix(fullPathResolved).toArray[PsiElement]
    }
  }
}

class HoconGotoSuperHandler extends CodeInsightActionHandler {
  def invoke(project: Project, editor: Editor, file: PsiFile): Unit = for {
    elemAtOffset <- file.findElementAt(editor.getCaretModel.getOffset).opt
    fieldAtOffset <- elemAtOffset.parentOfType[HKeyedField]
    resCtx = ResolutionCtx(fieldAtOffset.getContainingFile, followIncludes = true)
    prevOccurrence <- fieldAtOffset.moreOccurrences(reverse = true, resCtx).nextOption
    containingFile <- prevOccurrence.getContainingFile.opt.flatMap(_.getVirtualFile.opt)
  } {
    val desc = PsiNavigationSupport.getInstance.createNavigatable(
      project, containingFile, prevOccurrence.getTextOffset)
    desc.navigate(true)
  }
}
