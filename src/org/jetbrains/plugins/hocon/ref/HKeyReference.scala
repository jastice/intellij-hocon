package org.jetbrains.plugins.hocon
package ref

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{ElementManipulators, PsiElement, PsiReference}
import org.jetbrains.plugins.hocon.psi._

import scala.collection.mutable

/**
 * @author ghik
 */
class HKeyReference(key: HKey) extends PsiReference {
  def getCanonicalText: String = key.stringValue

  def getElement: PsiElement = key

  def isReferenceTo(element: PsiElement): Boolean = element == key

  def bindToElement(element: PsiElement): PsiElement = null

  def handleElementRename(newElementName: String): PsiElement =
    ElementManipulators.handleContentChange(key, newElementName)

  def isSoft = true

  def getRangeInElement: TextRange = ElementManipulators.getValueTextRange(key)

  // There's no single definition of a key that we could point to, just resolve to itself and leave the
  // job to each particular action, e.g. HoconGotoDeclarationHandler
  def resolve(): PsiElement = key

  // completion
  override def getVariants: Array[AnyRef] = {
    val file = key.hoconFile
    val toplevelCtx = ToplevelCtx(file)
    val opts = ResOpts(reverse = true)

    val variantFields: Iterator[ResolvedField] = key.parent match {
      case path: HPath => path.prefix match {
        case Some(prefixPath) =>
          prefixPath.allKeys.flatMapIt { path =>
            val strPath = path.map(_.stringValue)
            toplevelCtx.occurrences(strPath, opts).flatMap(_.subOccurrences(None, opts))
          }
        case None =>
          toplevelCtx.occurrences(None, opts)
      }
      case field: HKeyedField => field.prefixingField match {
        case Some(prefixField) => prefixField.fullContainingPath.iterator.flatMap {
          case (entries, path) =>
            val strPath = path.map(_.stringValue)
            val prefixOccurrences =
              if (entries.isToplevel) toplevelCtx.occurrences(strPath, opts)
              else entries.occurrences(strPath, opts, toplevelCtx)
            prefixOccurrences.flatMap(_.subOccurrences(None, opts))
        }
        case None =>
          val entries = field.enclosingEntries
          if (entries.isToplevel) toplevelCtx.occurrences(None, opts)
          else entries.occurrences(None, opts, toplevelCtx)
      }
    }

    val seenKeys = new mutable.HashSet[String]
    variantFields.filter(sf => seenKeys.add(sf.key)) // dirty, stateful filter
      .map(sf => new HoconFieldLookupElement(sf))
      .toArray[AnyRef]
  }
}

class HoconFieldLookupElement(resField: ResolvedField) extends LookupElement {
  def getLookupString: String = resField.field.key.fold("")(_.getText)

  override def renderElement(presentation: LookupElementPresentation): Unit = {
    super.renderElement(presentation)
    presentation.setIcon(PropertyIcon)
  }

  override def getObject: ResolvedField = resField

  override def getPsiElement: PsiElement = resField.field
}
